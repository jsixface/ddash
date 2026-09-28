package io.gh.jsixface.ddash.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** State kept between redirecting to the IdP and handling its callback. */
data class PendingLogin(val codeVerifier: String, val returnTo: String)

/**
 * In-memory login sessions. Sessions are intentionally not persisted: a restart just means users log in again.
 * Both maps are bounded so unauthenticated traffic to `/auth/login` cannot grow memory without limit.
 */
class SessionStore(
    private val sessionTtl: Duration,
    private val loginTtl: Duration = 10.minutes,
    private val maxEntries: Int = 1000,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private class Timed<T>(val value: T, val expires: TimeMark)

    private val lock = Mutex()
    private val sessions = LinkedHashMap<String, Timed<AuthUser>>()
    private val logins = LinkedHashMap<String, Timed<PendingLogin>>()

    suspend fun createSession(user: AuthUser): String = lock.withLock {
        val id = Crypto.randomToken()
        put(sessions, id, user, sessionTtl)
        id
    }

    suspend fun getSession(id: String): AuthUser? = lock.withLock {
        val entry = sessions[id] ?: return@withLock null
        if (entry.expires.hasPassedNow()) {
            sessions.remove(id)
            null
        } else entry.value
    }

    suspend fun deleteSession(id: String) = lock.withLock { sessions.remove(id); Unit }

    suspend fun savePendingLogin(state: String, login: PendingLogin) = lock.withLock {
        put(logins, state, login, loginTtl)
    }

    /** Returns and removes the pending login for [state] (single use). */
    suspend fun takePendingLogin(state: String): PendingLogin? = lock.withLock {
        val entry = logins.remove(state) ?: return@withLock null
        if (entry.expires.hasPassedNow()) null else entry.value
    }

    private fun <T> put(map: LinkedHashMap<String, Timed<T>>, key: String, value: T, ttl: Duration) {
        map.entries.removeAll { it.value.expires.hasPassedNow() }
        while (map.size >= maxEntries) map.remove(map.keys.first())
        map[key] = Timed(value, timeSource.markNow() + ttl)
    }
}
