package io.gh.jsixface.ddash.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTime::class)
class SessionStoreTest {
    private val user = AuthUser(subject = "u1", name = "Alice")

    @Test
    fun `session expires after ttl`() = runTest {
        val time = TestTimeSource()
        val store = SessionStore(sessionTtl = 1.hours, timeSource = time)
        val id = store.createSession(user)

        assertEquals(user, store.getSession(id))
        time += 59.minutes
        assertNotNull(store.getSession(id))
        time += 2.minutes
        assertNull(store.getSession(id))
    }

    @Test
    fun `logout removes the session`() = runTest {
        val store = SessionStore(sessionTtl = 1.hours)
        val id = store.createSession(user)
        store.deleteSession(id)
        assertNull(store.getSession(id))
    }

    @Test
    fun `pending login is single use and expires`() = runTest {
        val time = TestTimeSource()
        val store = SessionStore(sessionTtl = 1.hours, loginTtl = 10.minutes, timeSource = time)
        store.savePendingLogin("s1", PendingLogin("v", "/x"))
        assertEquals(PendingLogin("v", "/x"), store.takePendingLogin("s1"))
        assertNull(store.takePendingLogin("s1"))

        store.savePendingLogin("s2", PendingLogin("v", "/"))
        time += 11.minutes
        assertNull(store.takePendingLogin("s2"))
    }

    @Test
    fun `store is bounded`() = runTest {
        val store = SessionStore(sessionTtl = 1.hours, maxEntries = 3)
        val ids = (1..5).map { store.createSession(user) }
        assertNull(store.getSession(ids[0]))
        assertNull(store.getSession(ids[1]))
        assertNotNull(store.getSession(ids[4]))
    }

    @Test
    fun `returnTo only allows local paths`() {
        assertEquals("/", AuthService.sanitizeReturnTo(null))
        assertEquals("/apps?x=1", AuthService.sanitizeReturnTo("/apps?x=1"))
        assertEquals("/", AuthService.sanitizeReturnTo("https://evil.example"))
        assertEquals("/", AuthService.sanitizeReturnTo("//evil.example"))
        assertEquals("/", AuthService.sanitizeReturnTo("/\\evil.example"))
        assertEquals("/", AuthService.sanitizeReturnTo("/a\r\nSet-Cookie: x=y"))
    }
}
