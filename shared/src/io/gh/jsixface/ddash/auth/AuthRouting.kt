package io.gh.jsixface.ddash.auth

import co.touchlab.kermit.Logger
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.http.CacheControl
import kotlinx.serialization.Serializable

const val SESSION_COOKIE = "ddash_session"

@Serializable
data class SessionInfo(
    /** True when OIDC login is configured. When false, every action is available to everyone. */
    val authEnabled: Boolean,
    /** True when the caller may use privileged actions (start/stop/restart/logs). */
    val canManage: Boolean,
    val user: SessionUser? = null,
)

@Serializable
data class SessionUser(val name: String, val email: String? = null)

@Serializable
data class ErrorResponse(val error: String)

/** `/auth/login`, `/auth/callback`, `/auth/logout` and `/api/session`. */
fun Route.authRoutes(auth: AuthService) {
    val logger = Logger.withTag("AuthRouting")

    get("/api/session") {
        call.noStore()
        val user = call.currentUser(auth)
        call.respond(
            SessionInfo(
                authEnabled = auth.enabled,
                canManage = !auth.enabled || user != null,
                user = user?.let { SessionUser(it.displayName, it.email) },
            )
        )
    }

    if (!auth.enabled) return

    get("/auth/login") {
        call.noStore()
        val returnTo = call.request.queryParameters["returnTo"]
        try {
            call.respondRedirect(auth.beginLogin(returnTo ?: "/"))
        } catch (e: OidcException) {
            logger.e(e) { "Cannot start OIDC login" }
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("Identity provider is unavailable"))
        }
    }

    get("/auth/callback") {
        call.noStore()
        val params = call.request.queryParameters
        val code = params["code"]
        val state = params["state"]
        if (params["error"] != null || code == null || state == null) {
            logger.w { "OIDC callback without code: error=${params["error"]}" }
            return@get call.respondRedirect("/?auth_error=denied")
        }
        try {
            val (sessionId, returnTo) = auth.completeLogin(code, state)
            call.response.cookies.append(sessionCookie(sessionId, auth, maxAgeSeconds = null))
            call.respondRedirect(returnTo)
        } catch (e: AuthException) {
            call.respondRedirect(if (e.forbidden) "/?auth_error=forbidden" else "/?auth_error=failed")
        } catch (e: OidcException) {
            logger.e(e) { "OIDC login failed" }
            call.respondRedirect("/?auth_error=failed")
        }
    }

    post("/auth/logout") {
        call.noStore()
        call.request.cookies[SESSION_COOKIE]?.let { auth.sessions.deleteSession(it) }
        call.response.cookies.append(sessionCookie("", auth, maxAgeSeconds = 0))
        call.respond(HttpStatusCode.NoContent)
    }
}

/** The logged-in user for this request, or null when anonymous (or auth is disabled). */
suspend fun ApplicationCall.currentUser(auth: AuthService): AuthUser? =
    if (auth.enabled) auth.userForSession(request.cookies[SESSION_COOKIE]) else null

/**
 * Guard for privileged endpoints. Returns true when the caller may proceed; otherwise a 401 has been sent and the
 * handler must return. Always true when OIDC is not configured.
 */
suspend fun ApplicationCall.requireAccess(auth: AuthService): Boolean {
    if (!auth.enabled || currentUser(auth) != null) return true
    noStore()
    respond(HttpStatusCode.Unauthorized, ErrorResponse("Login required"))
    return false
}

private fun sessionCookie(value: String, auth: AuthService, maxAgeSeconds: Int?) = Cookie(
    name = SESSION_COOKIE,
    value = value,
    encoding = CookieEncoding.RAW,
    maxAge = maxAgeSeconds,
    path = "/",
    secure = auth.secureCookies,
    httpOnly = true,
    // Lax keeps the cookie off cross-site POSTs, which is what protects start/stop/restart from CSRF.
    extensions = mapOf("SameSite" to "Lax"),
)

private fun ApplicationCall.noStore() {
    response.cacheControl(CacheControl.NoStore(null))
    response.header(HttpHeaders.Pragma, "no-cache")
}
