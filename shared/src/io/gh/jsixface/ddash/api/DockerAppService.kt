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
            logger.d { "Fetching containers from Docker API" }
            val containers = apiClient.listContainers()
            logger.i { "Found ${containers.size} containers" }
            containers.mapIndexedNotNull {  index, container ->
                mapToAppData(index, container)
            }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching data from Docker API" }
            emptyList()
        }
    }

    private fun mapToAppData(index: Int, container: DockerContainer): AppData? {
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
            id = index,
            name = name,
            url = route,
            category = category,
            status = status,
            icon = icon
        )
    }
}
