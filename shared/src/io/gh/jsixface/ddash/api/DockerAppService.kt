package io.gh.jsixface.ddash.api

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer

class DockerAppService(private val apiClient: DockerApiClient) {
    private val logger = Logger.withTag("DockerAppService")

    // Get the docker container details through docker sock API.
    // Extract the label metadata from containers and convert to AppData
    suspend fun getAppData(): List<AppData> {
        return try {
            logger.d { "Fetching containers from Docker API" }
            val containers = apiClient.listContainers()
            logger.i { "Found ${containers.size} containers" }
            containers.mapIndexed { index, container ->
                mapToAppData(index, container)
            }
        } catch (e: Exception) {
            logger.e(e) { "Error fetching data from Docker API" }
            emptyList()
        }
    }

    private fun mapToAppData(index: Int, container: DockerContainer): AppData {
        val labels = container.labels
        val name = labels[DashLabels.Name.label] ?: container.names.firstOrNull()?.removePrefix("/") ?: container.id
        val url = labels[DashLabels.Url.label] ?: ""
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
            url = url,
            category = category,
            status = status,
            icon = icon
        )
    }
}
