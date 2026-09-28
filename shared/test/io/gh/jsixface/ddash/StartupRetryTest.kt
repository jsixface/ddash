package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.docker.DockerApiClient
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalAtomicApi::class)
class StartupRetryTest {
    private class FlakyCaddy(private val failures: Int, private val delegate: FakeCaddyApi) : io.gh.jsixface.ddash.caddy.CaddyApi by delegate {
        val checks = AtomicInt(0)
        override suspend fun checkConnectivity(): Boolean = checks.addAndFetch(1) > failures
    }

    @Test
    fun `waits for Caddy to come up then routes containers`() = runBlocking {
        val fake = FakeCaddyApi()
        val caddy = FlakyCaddy(failures = 3, delegate = fake)
        val docker: DockerApiClient = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))
        val coordinator = StartupCoordinator(docker, caddy, retryInitialDelay = 5.milliseconds, retryMaxDelay = 20.milliseconds)

        coordinator.run()
        coordinator.stopMonitoring()

        assertEquals(4, caddy.checks.load())
        assertEquals(listOf(listOf("a.local")), fake.hostsOn("srv80"))
    }

    @Test
    fun `gives up after maxAttempts without touching Caddy`() = runBlocking {
        val fake = FakeCaddyApi()
        val caddy = FlakyCaddy(failures = Int.MAX_VALUE, delegate = fake)
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))
        val coordinator = StartupCoordinator(docker, caddy, retryInitialDelay = 1.milliseconds, maxAttempts = 3)

        coordinator.run()

        assertEquals(3, caddy.checks.load())
        assertTrue(fake.hostsOn("srv80").isEmpty())
    }
}
