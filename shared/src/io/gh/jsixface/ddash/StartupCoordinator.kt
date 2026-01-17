package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.caddy.HttpCaddyApi
import io.gh.jsixface.ddash.docker.DockerApiClient

class StartupCoordinator(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi = HttpCaddyApi(),
) {
    private val logger = Logger.withTag("StartupCoordinator")
    private val routeManager = RouteManager(dockerClient, caddyApi)
    private val eventMonitor = DockerEventMonitor(dockerClient) { _, _ ->
        routeManager.processContainers()
    }

    suspend fun run() {
        logger.i { "Starting DDash startup checks..." }

        val dockerOk = dockerClient.ping()
        val caddyOk = caddyApi.checkConnectivity()

        if (dockerOk && caddyOk) {
            logger.i { "Connectivity to Docker and Caddy established." }
            routeManager.processContainers()
            eventMonitor.start()
        } else {
            if (!dockerOk) logger.e { "Docker connectivity check failed." }
            if (!caddyOk) logger.e { "Caddy connectivity check failed." }
        }
    }

    fun stopMonitoring() = eventMonitor.stop()
}
