package io.gh.jsixface.ddash.api

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.Globals
import io.gh.jsixface.ddash.docker.ContainerNotFoundException
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.isHttps
import kotlinx.coroutines.flow.Flow

open class DockerAppService(private val apiClient: DockerApiClient) {
    private val logger = Logger.withTag("DockerAppService")
    private val settings = Globals.settings

    // Get the docker container details through docker sock API.
    // Extract the label metadata from containers and convert to AppData
    // Errors from the Docker API propagate: an empty list must mean "no apps", not "Docker is broken".
    open suspend fun getAppData(): List<AppData> {
        val containers = apiClient.listContainers()
        logger.i { "Found ${containers.size} containers" }
        return containers.mapNotNull { container ->
            mapToAppData(container)
        }.sortedBy { it.category }
    }

    private fun mapToAppData(container: DockerContainer): AppData? {
        val labels = container.labels
        val enabled = labels[DashLabels.Enable.label]?.toBoolean() ?: false
        // If not explicitly enabled, we don't show it on dashboard.
        if (!enabled) return null
        val name = labels[DashLabels.Name.label] ?: container.names.firstOrNull()?.removePrefix("/") ?: container.id
        val secure = labels.isHttps(settings.caddySecureRouting)
        val route = (labels[DashLabels.Url.label] ?: labels[DashLabels.Route.label])
            ?.let { normalizeUrl(it, secure) }
            ?: ""
        val category = labels[DashLabels.Category.label] ?: "Uncategorized"
        val icon = labels[DashLabels.Icon.label] ?: "LayoutGrid"
        val description = labels[DashLabels.Description.label]
        val order = labels[DashLabels.Order.label]?.toIntOrNull() ?: Int.MAX_VALUE
        val status = try {
            AppStatus.valueOf(container.state.uppercase())
        } catch (e: IllegalArgumentException) {
            AppStatus.CREATED
        }

        val health = when {
            container.status.contains("(healthy)") -> HealthStatus.HEALTHY
            container.status.contains("(unhealthy)") -> HealthStatus.UNHEALTHY
            container.status.contains("(health: starting)") -> HealthStatus.STARTING
            else -> HealthStatus.NONE
        }

        return AppData(
            id = container.id,
            name = name,
            url = route,
            category = category,
            status = status,
            icon = icon,
            description = description,
            order = order,
            health = health
        )
    }

    /**
     * Log stream for a container DDash manages.
     * @throws ContainerNotFoundException if [id] is not an enabled container.
     */
    suspend fun getLogs(id: String, timestamps: Boolean): Flow<String> {
        val containerId = requireManaged(id)
        logger.i { "Fetching logs for container $containerId" }
        return apiClient.containerLogs(containerId, tail = 100, follow = true, timestamps = timestamps)
    }

    suspend fun stop(id: String) {
        val containerId = requireManaged(id)
        logger.i { "Stopping container $containerId" }
        apiClient.stopContainer(containerId)
    }

    suspend fun restart(id: String) {
        val containerId = requireManaged(id)
        logger.i { "Restarting container $containerId" }
        apiClient.restartContainer(containerId)
    }

    suspend fun start(id: String) {
        val containerId = requireManaged(id)
        logger.i { "Starting container $containerId" }
        apiClient.startContainer(containerId)
    }

    /**
     * Only containers that opted in with `ddash.enable=true` may be controlled through the dashboard. The id used
     * for the Docker call is the one Docker reported, never the raw request value.
     */
    private suspend fun requireManaged(id: String): String {
        val container = apiClient.listContainers().find {
            it.id == id && it.labels[DashLabels.Enable.label]?.toBoolean() == true
        }
        if (container == null) {
            logger.w { "Rejected request for unknown or unmanaged container '${id.take(64)}'" }
            throw ContainerNotFoundException(id)
        }
        return container.id
    }

    /**
     * Adds the scheme to bare hosts and drops explicit schemes other than http(s). The value ends up as a link in the
     * dashboard, so it must never be a `javascript:` (or similar) URL.
     */
    private fun normalizeUrl(raw: String, secure: Boolean): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val hasScheme = "://" in value
        if (hasScheme) {
            val scheme = value.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") {
                logger.w { "Ignoring URL with unsupported scheme: $value" }
                return null
            }
            return value
        }
        return (if (secure) "https://" else "http://") + value
    }
}
