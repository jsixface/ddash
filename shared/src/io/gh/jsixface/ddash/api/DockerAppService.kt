package io.gh.jsixface.ddash.api

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.Globals
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer

class DockerAppService(private val apiClient: DockerApiClient) {
    private val logger = Logger.withTag("DockerAppService")
    private val settings = Globals.settings

    // Get the docker container details through docker sock API.
    // Extract the label metadata from containers and convert to AppData
    suspend fun getAppData(): List<AppData> {
        return try {
            val containers = apiClient.listContainers()
            logger.i { "Found ${containers.size} containers" }
            containers.mapNotNull { container ->
                mapToAppData(container)
            }.sortedBy { it.category }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching data from Docker API" }
            emptyList()
        }
    }

    private fun mapToAppData(container: DockerContainer): AppData? {
        val labels = container.labels
        val enabled = labels[DashLabels.Enable.label]?.toBoolean() ?: false
        // If not explicitly enabled, we don't show it on dashboard.
        if (!enabled) return null
        val name = labels[DashLabels.Name.label] ?: container.names.firstOrNull()?.removePrefix("/") ?: container.id
        val route = labels[DashLabels.Route.label]?.let {
            (if (settings.caddySecureRouting) "https://" else "http://") + it
        } ?: ""
        val category = labels[DashLabels.Category.label] ?: "Uncategorized"
        val icon = labels[DashLabels.Icon.label] ?: "LayoutGrid"
        val status = try {
            AppStatus.valueOf(container.state.uppercase())
        } catch (e: IllegalArgumentException) {
            AppStatus.CREATED
        }

        return AppData(
            id = container.id,
            name = name,
            url = route,
            category = category,
            status = status,
            icon = icon
        )
    }

    fun getLogs(id: String, timestamps: Boolean): kotlinx.coroutines.flow.Flow<String> {
        logger.i { "Fetching logs for container $id" }
        return apiClient.containerLogs(id, tail = 100, follow = true, timestamps = timestamps)
    }

    suspend fun stop(id: String) {
        try {
            logger.i { "Stopping container $id" }
            apiClient.stopContainer(id)
        } catch (e: Exception) {
            logger.e(e) { "Error stopping container $id" }
        }
    }

    suspend fun restart(id: String) {
        try {
            logger.i { "Restarting container $id" }
            apiClient.restartContainer(id)
        } catch (e: Exception) {
            logger.e(e) { "Error restarting container $id" }
        }
    }

    suspend fun start(id: String) {
        try {
            logger.i { "Starting container $id" }
            apiClient.startContainer(id)
        } catch (e: Exception) {
            logger.e(e) { "Error starting container $id" }
        }
    }
}
