package io.gh.jsixface.ddash.server

import io.gh.jsixface.ddash.ExternalConfigService
import io.gh.jsixface.ddash.FakeCaddyApi
import io.gh.jsixface.ddash.FakeDockerApiClient
import io.gh.jsixface.ddash.OidcSettings
import io.gh.jsixface.ddash.api.AppService
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.auth.AuthService
import io.gh.jsixface.ddash.auth.OidcClient
import io.gh.jsixface.ddash.managedContainer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class AuthRoutingTest {
    private val issuer = "https://idp.example"
    private val redirect = "https://dash.example/auth/callback"

    private class Env(
        val docker: FakeDockerApiClient,
        val deps: ServerDependencies,
        val idpRequests: MutableList<String>,
        val tokenForms: MutableList<String>,
        val tokenAuthHeaders: MutableList<String?>,
    )

    private fun settings(allowed: Set<String> = emptySet()) = OidcSettings(
        issuerUrl = issuer,
        clientId = "ddash",
        clientSecret = "s3cret",
        redirectUri = redirect,
        allowedUsers = allowed,
    )

    private fun env(oidc: OidcSettings?, userinfo: String = """{"sub":"u1","email":"alice@example.com","name":"Alice"}"""): Env {
        val docker = FakeDockerApiClient(
            listOf(
                managedContainer("c1", "web", route = "web.local"),
                managedContainer("c2", "hidden").copy(labels = emptyMap()), // not opted in
            )
        )
        val idpRequests = mutableListOf<String>()
        val tokenForms = mutableListOf<String>()
        val tokenAuth = mutableListOf<String?>()
        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val idp = HttpClient(MockEngine { request ->
            idpRequests += "${request.method.value} ${request.url}"
            when (request.url.encodedPath) {
                "/.well-known/openid-configuration" -> respond(
                    """{"issuer":"$issuer","authorization_endpoint":"$issuer/authorize","token_endpoint":"$issuer/token","userinfo_endpoint":"$issuer/userinfo"}""",
                    HttpStatusCode.OK, json,
                )

                "/token" -> {
                    tokenForms += request.body.toByteArray().decodeToString()
                    tokenAuth += request.headers[HttpHeaders.Authorization]
                    respond("""{"access_token":"at-1","id_token":"a.b.c","token_type":"Bearer"}""", HttpStatusCode.OK, json)
                }

                "/userinfo" -> {
                    assertEquals("Bearer at-1", request.headers[HttpHeaders.Authorization])
                    respond(userinfo, HttpStatusCode.OK, json)
                }

                else -> respond("nope", HttpStatusCode.NotFound)
            }
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val dockerApp = DockerAppService(docker)
        val auth = AuthService(oidc, oidc?.let { OidcClient(it, idp) })
        val deps = ServerDependencies(
            appService = AppService(dockerApp, ExternalConfigService("/nonexistent/services.toml"), FakeCaddyApi()),
            dockerAppService = dockerApp,
            auth = auth,
        )
        return Env(docker, deps, idpRequests, tokenForms, tokenAuth)
    }

    private fun withServer(env: Env, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configureServer(env.deps) }
        block()
    }

    /** Runs the login round trip and returns the `Cookie` header value for the new session. */
    private suspend fun ApplicationTestBuilder.login(returnTo: String? = null): String {
        val client = createClient { followRedirects = false }
        val start = client.get("/auth/login" + (returnTo?.let { "?returnTo=$it" } ?: ""))
        assertEquals(HttpStatusCode.Found, start.status)
        val authorize = Url(start.headers[HttpHeaders.Location]!!)
        val state = authorize.parameters["state"]!!
        val callback = client.get("/auth/callback?code=the-code&state=$state")
        assertEquals(HttpStatusCode.Found, callback.status, callback.bodyAsText())
        return callback.sessionCookie() ?: error("no session cookie set")
    }

    private fun HttpResponse.sessionCookie(): String? =
        headers.getAll(HttpHeaders.SetCookie)?.firstOrNull { it.startsWith("ddash_session=") }
            ?.substringBefore(';')?.takeIf { it != "ddash_session=" }

    @Test
    fun `anonymous users can view apps but not control containers`() {
        val env = env(settings())
        withServer(env) {
            assertEquals(HttpStatusCode.OK, client.get("/api/apps").status)
            assertTrue(client.get("/api/apps").bodyAsText().contains("web"))

            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/app/c1/stop").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/app/c1/restart").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/app/c1/start").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/app/c1/logs").status)
            assertTrue(env.docker.actions.isEmpty())

            val session = client.get("/api/session").bodyAsText()
            assertContains(session, "\"authEnabled\": true")
            assertContains(session, "\"canManage\": false")
        }
    }

    @Test
    fun `login redirects to the provider with state and PKCE`() {
        val env = env(settings())
        withServer(env) {
            val client = createClient { followRedirects = false }
            val response = client.get("/auth/login")
            assertEquals(HttpStatusCode.Found, response.status)
            val location = Url(response.headers[HttpHeaders.Location]!!)
            assertEquals("$issuer/authorize", "${location.protocol.name}://${location.host}${location.encodedPath}")
            assertEquals("code", location.parameters["response_type"])
            assertEquals("ddash", location.parameters["client_id"])
            assertEquals(redirect, location.parameters["redirect_uri"])
            assertEquals("S256", location.parameters["code_challenge_method"])
            assertNotNull(location.parameters["state"])
            assertNotNull(location.parameters["code_challenge"])
            assertContains(location.parameters["scope"]!!, "openid")
        }
    }

    @Test
    fun `full login enables container actions and logout revokes them`() {
        val env = env(settings())
        withServer(env) {
            val cookie = login()

            assertEquals(HttpStatusCode.OK, client.post("/api/app/c1/restart") { header(HttpHeaders.Cookie, cookie) }.status)
            assertEquals(listOf("restart:c1"), env.docker.actions)
            val logs = client.get("/api/app/c1/logs") { header(HttpHeaders.Cookie, cookie) }
            assertEquals(HttpStatusCode.OK, logs.status)
            assertEquals("line 1\n", logs.bodyAsText())

            val session = client.get("/api/session") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
            assertContains(session, "\"canManage\": true")
            assertContains(session, "Alice")

            assertEquals(HttpStatusCode.NoContent, client.post("/auth/logout") { header(HttpHeaders.Cookie, cookie) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/app/c1/stop") { header(HttpHeaders.Cookie, cookie) }.status)
        }
        // Confidential client: credentials in the Authorization header, PKCE verifier in the body.
        assertTrue(env.tokenAuthHeaders.single()!!.startsWith("Basic "))
        assertContains(env.tokenForms.single(), "code_verifier=")
        assertContains(env.tokenForms.single(), "grant_type=authorization_code")
        assertFalse(env.tokenForms.single().contains("s3cret"))
    }

    @Test
    fun `session cookie is httpOnly and secure for https and SameSite Lax`() {
        val env = env(settings())
        withServer(env) {
            val client = createClient { followRedirects = false }
            val state = Url(client.get("/auth/login").headers[HttpHeaders.Location]!!).parameters["state"]!!
            val header = client.get("/auth/callback?code=c&state=$state").headers.getAll(HttpHeaders.SetCookie)!!.single()
            assertContains(header, "HttpOnly")
            assertContains(header, "Secure")
            assertContains(header, "SameSite=Lax")
            assertContains(header, "Path=/")
        }
    }

    @Test
    fun `callback with unknown or reused state is rejected`() {
        val env = env(settings())
        withServer(env) {
            val client = createClient { followRedirects = false }
            val bogus = client.get("/auth/callback?code=c&state=nope")
            assertEquals("/?auth_error=failed", bogus.headers[HttpHeaders.Location])
            assertNull(bogus.sessionCookie())

            val state = Url(client.get("/auth/login").headers[HttpHeaders.Location]!!).parameters["state"]!!
            assertNotNull(client.get("/auth/callback?code=c&state=$state").sessionCookie())
            val replay = client.get("/auth/callback?code=c&state=$state")
            assertNull(replay.sessionCookie())
        }
    }

    @Test
    fun `provider error on callback sends the user back without a session`() {
        withServer(env(settings())) {
            val client = createClient { followRedirects = false }
            val response = client.get("/auth/callback?error=access_denied")
            assertEquals("/?auth_error=denied", response.headers[HttpHeaders.Location])
            assertNull(response.sessionCookie())
        }
    }

    @Test
    fun `returnTo is honoured only for local paths`() {
        withServer(env(settings())) {
            val client = createClient { followRedirects = false }
            fun stateFor(returnTo: String) = client.let { c ->
                kotlinx.coroutines.runBlocking {
                    Url(c.get("/auth/login?returnTo=$returnTo").headers[HttpHeaders.Location]!!).parameters["state"]!!
                }
            }
            val local = client.get("/auth/callback?code=c&state=${stateFor("/deep/link")}")
            assertEquals("/deep/link", local.headers[HttpHeaders.Location])
            val external = client.get("/auth/callback?code=c&state=${stateFor("https://evil.example")}")
            assertEquals("/", external.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `allow-list rejects other authenticated users`() {
        val env = env(settings(allowed = setOf("bob@example.com")))
        withServer(env) {
            val client = createClient { followRedirects = false }
            val state = Url(client.get("/auth/login").headers[HttpHeaders.Location]!!).parameters["state"]!!
            val callback = client.get("/auth/callback?code=c&state=$state")
            assertEquals("/?auth_error=forbidden", callback.headers[HttpHeaders.Location])
            assertNull(callback.sessionCookie())
        }
    }

    @Test
    fun `allow-list accepts a listed user case-insensitively`() {
        val env = env(settings(allowed = setOf("alice@example.com")), userinfo = """{"sub":"u1","email":"Alice@Example.com"}""")
        withServer(env) {
            login()
        }
    }

    @Test
    fun `only owned containers can be controlled`() {
        val env = env(settings())
        withServer(env) {
            val cookie = login()
            // Exists in Docker but has no ddash.enable label.
            assertEquals(HttpStatusCode.NotFound, client.post("/api/app/c2/stop") { header(HttpHeaders.Cookie, cookie) }.status)
            // Not a container at all, including path-traversal style values.
            assertEquals(HttpStatusCode.NotFound, client.post("/api/app/nope/stop") { header(HttpHeaders.Cookie, cookie) }.status)
            assertEquals(HttpStatusCode.NotFound, client.post("/api/app/..%2Fvolumes%2Fprune%3F/stop") { header(HttpHeaders.Cookie, cookie) }.status)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/app/c2/logs") { header(HttpHeaders.Cookie, cookie) }.status)
            assertTrue(env.docker.actions.isEmpty())
        }
    }

    @Test
    fun `identity provider outage on login is reported as bad gateway`() {
        val env = env(settings())
        val broken = HttpClient(MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }) { expectSuccess = true }
        val auth = AuthService(settings(), OidcClient(settings(), broken))
        withServer(Env(env.docker, ServerDependencies(env.deps.appService, env.deps.dockerAppService, auth), mutableListOf(), mutableListOf(), mutableListOf())) {
            val client = createClient { followRedirects = false }
            assertEquals(HttpStatusCode.BadGateway, client.get("/auth/login").status)
        }
    }

    @Test
    fun `without OIDC everything stays open and login routes are absent`() {
        val env = env(null)
        withServer(env) {
            assertEquals(HttpStatusCode.OK, client.post("/api/app/c1/stop").status)
            assertEquals(listOf("stop:c1"), env.docker.actions)
            assertEquals(HttpStatusCode.NotFound, client.get("/auth/login").status)
            val session = client.get("/api/session").bodyAsText()
            assertContains(session, "\"authEnabled\": false")
            assertContains(session, "\"canManage\": true")
        }
    }

    @Test
    fun `docker failures surface as bad gateway not success`() {
        val env = env(null)
        val failing = object : io.gh.jsixface.ddash.docker.DockerApiClient by env.docker {
            override suspend fun listContainers() = throw io.gh.jsixface.ddash.docker.DockerApiException("down")
        }
        val dockerApp = DockerAppService(failing)
        val deps = ServerDependencies(AppService(dockerApp, ExternalConfigService("/nonexistent"), FakeCaddyApi()), dockerApp, env.deps.auth)
        testApplication {
            application { configureServer(deps) }
            assertEquals(HttpStatusCode.BadGateway, client.get("/api/apps").status)
            assertEquals(HttpStatusCode.BadGateway, client.post("/api/app/c1/stop").status)
        }
    }
}
