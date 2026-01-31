package io.gh.jsixface.ddash.api

import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class DockerAppServiceTest {

    class MockDockerApiClient(private val containers: List<DockerContainer>) : DockerApiClient {
        override suspend fun listImages(): List<DockerImage> = emptyList()
        override suspend fun listContainers(): List<DockerContainer> = containers
        override suspend fun ping(): Boolean = true
        override fun events(): Flow<DockerEvent> = emptyFlow()
        override fun containerLogs(
            containerId: String,
            tail: Int,
            follow: Boolean,
            timestamps: Boolean,
        ): Flow<String> = emptyFlow()

        override suspend fun stopContainer(containerId: String) {}
        override suspend fun restartContainer(containerId: String) {}
    }

    @Test
    fun `test mapping with labels`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/container1"),
            image = "image1",
            state = "running",
            status = "Up 2 hours",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Name.label to "Custom Name",
                DashLabels.Category.label to "Tools",
                DashLabels.Route.label to "localhost:8081",
                DashLabels.Icon.label to "Terminal"
            )
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        val app = result[0]
        assertEquals("Custom Name", app.name)
        assertEquals("Tools", app.category)
        assertEquals("http://localhost:8081", app.url)
        assertEquals("Terminal", app.icon)
        assertEquals(AppStatus.RUNNING, app.status)
    }

    @Test
    fun `test mapping without labels uses container name`() = runBlocking {
        val container = DockerContainer(
            id = "id2",
            names = listOf("/container2"),
            image = "image2",
            state = "exited",
            status = "Exited (0) 5 minutes ago",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        val app = result[0]
        assertEquals("container2", app.name)
        assertEquals("Uncategorized", app.category)
        assertEquals("", app.url)
        assertEquals("LayoutGrid", app.icon)
        assertEquals(AppStatus.EXITED, app.status)
    }

    @Test
    fun `test fallback when name is absent`() = runBlocking {
        val container = DockerContainer(
            id = "id3",
            names = emptyList(),
            image = "image3",
            state = "running",
            status = "Up",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals("id3", result[0].name)
    }
}
