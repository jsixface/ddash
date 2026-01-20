package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import kotlinx.coroutines.CancellationException

class RouteManager(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi,
) {
    private val logger = Logger.withTag("RouteManager")
    private val settings = Globals.settings

    suspend fun processContainers() {
        val containers = try {
            dockerClient.listContainers()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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

        val currentRoutes = try {
            caddyApi.getRoutes()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error fetching current routes from Caddy" }
            emptyList()
        }
        var changed = false

        appsToRoute.forEach { container ->
            val host = container.labels[DashLabels.Route.label]!!
            logger.d { "Checking container --- ${container.names}, ${container.image}, ${container.ports}" }
            if (!currentRoutes.contains(host)) {
                val containerName = container.names.firstOrNull()?.removePrefix("/") ?: container.id
                val isDdash = container.labels[DashLabels.Name.label] == "D-Dash" ||
                    container.image.contains("ddash", ignoreCase = true)

                val upstream = if (isDdash) {
                    "localhost:${settings.port}"
                } else {
                    val port = getContainerPort(container)
                    if (port == null) {
                        logger.e { "Could not determine port for container $containerName. Skipping route." }
                        return@forEach
                    }
                    "$containerName:$port"
                }

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

    private fun getContainerPort(container: DockerContainer): String? {
        val labelPort = container.labels[DashLabels.Port.label]
        if (labelPort != null) return labelPort

        val ports = container.ports?.map { it.privatePort }?.toSet() ?: emptySet()
        if (ports.isEmpty()) {
            logger.e { "No ports exposed and no ddash.port label found for container ${container.names}" }
            return null
        }

        if (ports.size > 1) {
            logger.e { "Multiple ports exposed and no ddash.port label found for container ${container.names}. Ports: $ports" }
            return null
        }

        return ports.first().toString()
    }
}
