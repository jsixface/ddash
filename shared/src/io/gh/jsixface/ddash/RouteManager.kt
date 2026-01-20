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
        val containers = fetchContainers() ?: return

        val appsToRoute = containers.filter { container ->
            container.labels[DashLabels.Enable.label]?.toBoolean() == true &&
                container.labels.containsKey(DashLabels.Route.label)
        }

        if (appsToRoute.isEmpty()) {
            logger.i { "No containers found with ddash.route label." }
            return
        }

        val currentRoutes = fetchCurrentRoutes()
        var changed = false

        appsToRoute.forEach { container ->
            val host = container.labels[DashLabels.Route.label]!!
            logger.d { "Checking container --- ${container.names}, ${container.image}, ${container.ports}" }
            if (!currentRoutes.contains(host)) {
                val upstream = getUpstream(container, containers) ?: return@forEach
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

    private suspend fun fetchContainers(): List<DockerContainer>? {
        return try {
            dockerClient.listContainers()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to list containers" }
            null
        }
    }

    private suspend fun fetchCurrentRoutes(): List<String> {
        return try {
            caddyApi.getRoutes()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error fetching current routes from Caddy" }
            emptyList()
        }
    }

    private fun getUpstream(container: DockerContainer, allContainers: List<DockerContainer>): String? {
        val containerName = container.names.firstOrNull()?.removePrefix("/") ?: container.id
        val isDdash = container.labels[DashLabels.Name.label] == "D-Dash" ||
            container.image.contains("ddash", ignoreCase = true)

        if (isDdash) {
            return "localhost:${settings.port}"
        }

        val networkMode = container.hostConfig?.networkMode
        val (finalHost, finalPort) = when {
            networkMode == "host" -> {
                val port = container.labels[DashLabels.Port.label]
                if (port == null) {
                    logger.e { "Network mode is 'host' but no ddash.port label found for $containerName. Skipping." }
                    return null
                }
                "host.docker.internal" to port
            }

            networkMode?.startsWith("container:") == true -> {
                val target = networkMode.removePrefix("container:")
                val targetContainerName = allContainers.find { it.id == target || it.id.startsWith(target) }
                    ?.names?.firstOrNull()?.removePrefix("/") ?: target

                val port = container.labels[DashLabels.Port.label]
                if (port == null) {
                    logger.e { "Network mode is attached to '$target' but no ddash.port label found for $containerName. Skipping." }
                    return null
                }
                targetContainerName to port
            }

            else -> {
                val port = getContainerPort(container)
                if (port == null) {
                    logger.e { "Could not determine port for container $containerName. Skipping route." }
                    return null
                }
                containerName to port
            }
        }
        return "$finalHost:$finalPort"
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
