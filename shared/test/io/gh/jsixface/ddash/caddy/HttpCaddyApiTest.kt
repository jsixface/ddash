package io.gh.jsixface.ddash.caddy

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class HttpCaddyApiTest {
    private val lenientJson = Json { ignoreUnknownKeys = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** A realistic Caddyfile-style config: a handler DDash doesn't model, a path-only matcher, an empty server. */
    private val servers = """
        {
          "srv0": {
            "listen": [":443"],
            "routes": [
              {"match":[{"host":["secure.local"]}],"handle":[{"handler":"subroute","routes":[{"handle":[{"handler":"rewrite","uri":"/x"},{"handler":"authentication","providers":{}},{"handler":"reverse_proxy","upstreams":[{"dial":"a:80"}]}]}]}],"terminal":true},
              {"match":[{"path":["/api/*"]}],"handle":[{"handler":"log_append","key":"x"}]}
            ]
          },
          "srv1": {"listen": [":80"]},
          "srv2": {"listen": [":8080"], "routes": [{"match":[{"host":["a.local","b.local"]}],"handle":[{"handler":"map"}]}]}
        }
    """.trimIndent()

    private fun api(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpCaddyApi(HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        })

    @Test
    fun `configs with unmodelled handlers and matchers still parse`() = runTest {
        val api = api { respond(servers, HttpStatusCode.OK, jsonHeaders) }

        assertEquals(listOf("secure.local", "a.local", "b.local"), api.getRoutes())
        val placements = api.getRoutePlacements()
        assertEquals(listOf("secure.local", "a.local", "b.local"), placements.map { it.host })
        assertEquals(listOf("a.local", "b.local"), placements.last().hosts)
        assertEquals("srv0", api.resolveServerId(secure = true))
        assertEquals("srv1", api.resolveServerId(secure = false))
    }

    @Test
    fun `unknown handlers decode to Unknown`() {
        val route = lenientJson.decodeFromString(
            CaddyRoute.serializer(),
            """{"handle":[{"handler":"rewrite","uri":"/x"},{"handler":"reverse_proxy","upstreams":[{"dial":"a:1"}]}]}""",
        )
        assertIs<CaddyHandler.Unknown>(route.handle[0])
        assertIs<CaddyHandler.ReverseProxy>(route.handle[1])
    }

    @Test
    fun `read failures throw instead of looking like an empty config`() = runTest {
        val api = api { respond("oops", HttpStatusCode.InternalServerError) }
        assertFailsWith<Exception> { api.getRoutePlacements() }
        assertFailsWith<Exception> { api.getRoutes() }
        assertFailsWith<Exception> { api.resolveServerId(true) }
    }

    @Test
    fun `write failures throw`() = runTest {
        val api = api { respond("bad", HttpStatusCode.BadRequest) }
        assertFailsWith<Exception> { api.addRoute("a.local", "a:80", "srv0") }
        assertFailsWith<Exception> { api.removeRoute("srv0", 3) }
        assertFailsWith<Exception> { api.saveConfig() }
    }

    @Test
    fun `addRoute puts a reverse proxy route at the front of the server`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body = ""

        val api = api { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK)
        }
        api.addRoute("a.local", "a:80", "srv0")

        assertEquals(HttpMethod.Put, method)
        assertEquals("/config/apps/http/servers/srv0/routes/0", path)
        assertTrue(body.contains("\"host\":[\"a.local\"]"), body)
        assertTrue(body.contains("\"handler\":\"reverse_proxy\""), body)
        assertTrue(body.contains("\"dial\":\"a:80\""), body)
    }

    @Test
    fun `connectivity check reports failures as false`() = runTest {
        assertTrue(api { respond("{}", HttpStatusCode.OK, jsonHeaders) }.checkConnectivity())
        assertFalse(api { respond("no", HttpStatusCode.ServiceUnavailable) }.checkConnectivity())
        assertFalse(api { throw kotlinx.io.IOException("refused") }.checkConnectivity())
    }

    @Test
    fun `created routes are tagged and placements expose id and upstream`() = runTest {
        var body = ""
        val created = api { request ->
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK)
        }
        created.addRoute("a.local", "a:80", "srv0")
        assertTrue(body.contains("\"@id\":\"ddash-srv0-a.local\""), body)

        val config = """{"srv0":{"listen":[":80"],"routes":[
            {"@id":"ddash-srv0-a.local","match":[{"host":["a.local"]}],"handle":[{"handler":"reverse_proxy","upstreams":[{"dial":"a:80"}]}]},
            {"match":[{"host":["multi.local","m2.local"]}],"handle":[{"handler":"reverse_proxy","upstreams":[{"dial":"m:80"}]}]},
            {"match":[{"host":["sub.local"]}],"handle":[{"handler":"subroute","routes":[]}]},
            {"match":[{"host":["lb.local"]}],"handle":[{"handler":"reverse_proxy","upstreams":[{"dial":"x:1"},{"dial":"y:1"}]}]}
        ]}}"""
        val placements = api { respond(config, HttpStatusCode.OK, jsonHeaders) }.getRoutePlacements().associateBy { it.host }

        assertEquals("ddash-srv0-a.local", placements.getValue("a.local").id)
        assertEquals("a:80", placements.getValue("a.local").upstream)
        assertTrue(placements.getValue("a.local").replaceable)
        assertFalse(placements.getValue("multi.local").replaceable) // shared with another host
        assertFalse(placements.getValue("sub.local").replaceable) // not a plain reverse proxy
        assertFalse(placements.getValue("lb.local").replaceable) // several upstreams
    }

    @Test
    fun `updateRoute replaces the route at its index`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body = ""
        val api = api { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK)
        }
        api.updateRoute(RoutePlacement("a.local", "srv0", 4, upstream = "old:1"), "a:8080")

        assertEquals(HttpMethod.Patch, method)
        assertEquals("/config/apps/http/servers/srv0/routes/4", path)
        assertTrue(body.contains("\"dial\":\"a:8080\""), body)
        assertTrue(body.contains("\"@id\":\"ddash-srv0-a.local\""), body)
    }
}
