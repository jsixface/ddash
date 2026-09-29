package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.caddy.HttpCaddyApi
import io.gh.jsixface.ddash.docker.DockerApiClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class StartupCoordinator(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi = HttpCaddyApi(),
    private val retryInitialDelay: Duration = 2.seconds,
    private val retryMaxDelay: Duration = 60.seconds,
    /** Give up after this many failed connectivity checks; null (the default) retries forever. */
    private val maxAttempts: Int? = null,
) {
    private val logger = Logger.withTag("StartupCoordinator")
    private val routeManager = RouteManager(dockerClient, caddyApi)
    private val eventMonitor = DockerEventMonitor(dockerClient) {
        routeManager.processContainers()
    }

    /**
     * Waits for Docker and Caddy to become reachable (they often start after us in a compose stack), then applies
     * routes once and starts watching Docker events. Suspends until then; run it off the main path.
     */
    suspend fun run() {
        logger.i { "Starting DDash startup checks..." }

        var attempt = 0
        var wait = retryInitialDelay
        while (true) {
            attempt++
            val dockerOk = dockerClient.ping()
            val caddyOk = caddyApi.checkConnectivity()

            if (dockerOk && caddyOk) {
                logger.i { "Connectivity to Docker and Caddy established." }
                routeManager.processContainers()
                eventMonitor.start()
                return
            }

            if (!dockerOk) logger.e { "Docker connectivity check failed." }
            if (!caddyOk) logger.e { "Caddy connectivity check failed." }
            if (maxAttempts != null && attempt >= maxAttempts) {
                logger.e { "Giving up after $attempt attempts; routes will not be managed." }
                return
            }
            logger.i { "Retrying in $wait..." }
            delay(wait)
            wait = (wait * 2).coerceAtMost(retryMaxDelay)
        }
    }

    fun stopMonitoring() = eventMonitor.stop()
}
