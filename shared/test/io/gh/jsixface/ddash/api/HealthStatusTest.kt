package io.gh.jsixface.ddash.api

import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class HealthStatusTest {

    @Test
    fun testHealthStatusParsing() = runBlocking {
        val apiClient = mock(DockerApiClient::class.java)
        val service = object : DockerAppService(apiClient) {}

        val containers = listOf(
            DockerContainer(id = "1", status = "Up 5 minutes (healthy)", labels = mapOf(DashLabels.Enable.label to "true")),
            DockerContainer(id = "2", status = "Up 5 minutes (unhealthy)", labels = mapOf(DashLabels.Enable.label to "true")),
            DockerContainer(id = "3", status = "Up 5 minutes (health: starting)", labels = mapOf(DashLabels.Enable.label to "true")),
            DockerContainer(id = "4", status = "Up 5 minutes", labels = mapOf(DashLabels.Enable.label to "true"))
        )

        `when`(apiClient.listContainers()).thenReturn(containers)

        val appData = service.getAppData()

        assertEquals(AppHealth.HEALTHY, appData.find { it.id == "1" }?.health)
        assertEquals(AppHealth.UNHEALTHY, appData.find { it.id == "2" }?.health)
        assertEquals(AppHealth.STARTING, appData.find { it.id == "3" }?.health)
        assertEquals(AppHealth.NONE, appData.find { it.id == "4" }?.health)
    }
}
