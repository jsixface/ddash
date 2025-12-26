package io.gh.jsixface.ddash

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    @Test
    fun testDefaultSettings() {
        val settings = Settings()
        // These might fail if environment variables are actually set in the test environment
        // But assuming a clean environment:
        assertEquals(8080, settings.port)
        assertEquals("0.0.0.0", settings.host)
        assertEquals("/var/run/docker.sock", settings.dockerSocket)
    }
}
