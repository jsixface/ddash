package io.gh.jsixface.ddash.auth

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.OidcSettings
import kotlin.time.Duration.Companion.hours

/**
 * Ties the OIDC client and session store together. When [settings] is null authentication is disabled and every
 * request is treated as authorized.
 */
class AuthService(
    private val settings: OidcSettings?,
    private val oidc: OidcClient? = null,
    val sessions: SessionStore = SessionStore((settings?.sessionTtlHours ?: 24).hours),
) {
    private val logger = Logger.withTag("AuthService")

    val enabled: Boolean get() = settings != null && oidc != null
    val secureCookies: Boolean get() = oidc?.redirectIsSecure ?: false

    /** Starts a login: remembers the PKCE verifier under a fresh `state` and returns the IdP URL to redirect to. */
    suspend fun beginLogin(returnTo: String): String {
        val client = checkNotNull(oidc) { "OIDC is not enabled" }
        val state = Crypto.randomToken()
        val verifier = Crypto.randomToken()
        val url = client.authorizationUrl(state, verifier)
        sessions.savePendingLogin(state, PendingLogin(verifier, sanitizeReturnTo(returnTo)))
        return url
    }

    /** Completes a login. Returns the new session id and where to send the user, or throws [AuthException]. */
    suspend fun completeLogin(code: String, state: String): Pair<String, String> {
        val client = checkNotNull(oidc) { "OIDC is not enabled" }
        val pending = sessions.takePendingLogin(state) ?: throw AuthException("Unknown or expired login attempt")
        val user = try {
            client.fetchUser(client.exchangeCode(code, pending.codeVerifier))
        } catch (e: OidcException) {
            logger.e(e) { "OIDC login failed" }
            throw AuthException("Login with the identity provider failed")
        }
        if (!isAllowed(user)) {
            logger.w { "User ${user.displayName} (${user.subject}) authenticated but is not in OIDC_ALLOWED_USERS" }
            throw AuthException("This account is not allowed to manage containers", forbidden = true)
        }
        logger.i { "User ${user.displayName} logged in" }
        return sessions.createSession(user) to pending.returnTo
    }

    fun isAllowed(user: AuthUser): Boolean {
        val allowed = settings?.allowedUsers.orEmpty()
        if (allowed.isEmpty()) return true
        return listOfNotNull(user.email, user.username, user.subject).any { it.lowercase() in allowed }
    }

    suspend fun userForSession(sessionId: String?): AuthUser? = sessionId?.let { sessions.getSession(it) }

    companion object {
        /** Only same-site relative paths are accepted, so the login redirect cannot be abused as an open redirect. */
        fun sanitizeReturnTo(value: String?): String {
            if (value == null || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) return "/"
            if (value.any { it == '\r' || it == '\n' }) return "/"
            return value
        }
    }
}

class AuthException(message: String, val forbidden: Boolean = false) : Exception(message)
