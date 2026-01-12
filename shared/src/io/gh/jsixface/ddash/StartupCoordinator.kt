package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient

class StartupCoordinator(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi = CaddyApi()
) {
    private val logger = Logger.withTag("StartupCoordinator")
    private val settings = Globals.settings

    suspend fun run() {
        logger.i { "Starting DDash startup checks..." }

        val dockerOk = dockerClient.ping()
        val caddyOk = caddyApi.checkConnectivity()

        if (dockerOk && caddyOk) {
            logger.i { "Connectivity to Docker and Caddy established." }
            processContainers()
        } else {
            if (!dockerOk) logger.e { "Docker connectivity check failed." }
            if (!caddyOk) logger.e { "Caddy connectivity check failed." }
        }
    }

    private suspend fun processContainers() {
        val containers = try {
            dockerClient.listContainers()
        } catch (e: Exception) {
            logger.e(e) { "Failed to list containers" }
            return
        }

        val appsToRoute = containers.filter { container ->
            container.labels[DashLabels.Enable.label]?.toBoolean() == true &&
                    container.labels.containsKey(DashLabels.Route.label)
        }

        if (appsToRoute.isEmpty()) {
            logger.i { "No containers found with ddash.route label." }
            return
        }

        val currentRoutes = caddyApi.getRoutes()
        var changed = false

        appsToRoute.forEach { container ->
            val host = container.labels[DashLabels.Route.label]!!
            if (!currentRoutes.contains(host)) {
                val containerName = container.names.firstOrNull()?.removePrefix("/") ?: container.id
                val port = container.labels[DashLabels.Port.label]
                    ?: container.ports.firstOrNull()?.privatePort?.toString()
                    ?: "80"

                val upstream = "$containerName:$port"
                caddyApi.addRoute(host, upstream)
                changed = true
            } else {
                logger.d { "Route for $host already exists in Caddy." }
            }
        }

        if (changed && settings.caddyAutoSaveConfig) {
            caddyApi.saveConfig()
        }
    }
}
