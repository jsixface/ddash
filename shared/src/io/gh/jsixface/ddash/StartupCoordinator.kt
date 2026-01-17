package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StartupCoordinator(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi = CaddyApi(),
) {
    private val logger = Logger.withTag("StartupCoordinator")
    private val settings = Globals.settings
    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun run() {
        logger.i { "Starting DDash startup checks..." }

        val dockerOk = dockerClient.ping()
        val caddyOk = caddyApi.checkConnectivity()

        if (dockerOk && caddyOk) {
            logger.i { "Connectivity to Docker and Caddy established." }
            processContainers()
            startEventMonitoring()
        } else {
            if (!dockerOk) logger.e { "Docker connectivity check failed." }
            if (!caddyOk) logger.e { "Caddy connectivity check failed." }
        }
    }

    fun stopMonitoring() {
        scope.cancel()
    }

    private fun startEventMonitoring() {
        scope.launch {
            logger.i { "Starting Docker event monitoring..." }
            while (true) {
                try {
                    dockerClient.events().collectLatest { event ->
                        logger.d { "Docker event received: ${event.type} - ${event.action}" }
                        if (event.type == "container") {
                            if (event.action in listOf("start", "stop", "die", "destroy", "rename", "update")) {
                                logger.i { "Container event [${event.action}] for ${event.actor.id}. Updating routes..." }
                                processContainers()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error in Docker event monitoring. Retrying in 5 seconds..." }
                    delay(5000)
                }
            }
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
                val port = getContainerPort(container)

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

    private fun getContainerPort(container: DockerContainer): String {
        logger.i { "Container --- ${container.names}, ${container.image}, ${container.ports}" }

        return (container.labels[DashLabels.Port.label]
            ?: container.ports?.firstOrNull()?.privatePort?.toString()
            ?: "80")
    }
}
