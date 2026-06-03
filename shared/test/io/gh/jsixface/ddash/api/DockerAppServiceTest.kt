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
        override suspend fun startContainer(containerId: String) {}
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
        assertEquals(HealthStatus.NONE, app.health)
        assertEquals(Int.MAX_VALUE, app.order)
    }

    @Test
    fun `test mapping with health status healthy`() = runBlocking {
        val container = DockerContainer(
            id = "id-healthy",
            names = listOf("/healthy"),
            image = "image",
            state = "running",
            status = "Up 2 hours (healthy)",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(HealthStatus.HEALTHY, result[0].health)
    }

    @Test
    fun `test mapping with health status unhealthy`() = runBlocking {
        val container = DockerContainer(
            id = "id-unhealthy",
            names = listOf("/unhealthy"),
            image = "image",
            state = "running",
            status = "Up 2 hours (unhealthy)",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(HealthStatus.UNHEALTHY, result[0].health)
    }

    @Test
    fun `test mapping with health status starting`() = runBlocking {
        val container = DockerContainer(
            id = "id-starting",
            names = listOf("/starting"),
            image = "image",
            state = "running",
            status = "Up 2 seconds (health: starting)",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(HealthStatus.STARTING, result[0].health)
    }

    @Test
    fun `test mapping with description label`() = runBlocking {
        val container = DockerContainer(
            id = "id4",
            names = listOf("/container4"),
            image = "image4",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Description.label to "This is a description"
            )
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        assertEquals("This is a description", result[0].description)
    }

    @Test
    fun `test mapping with order label`() = runBlocking {
        val container = DockerContainer(
            id = "id5",
            names = listOf("/container5"),
            image = "image5",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Order.label to "10"
            )
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        assertEquals(10, result[0].order)
    }

    @Test
    fun `test mapping with invalid order label defaults to Int MAX_VALUE`() = runBlocking {
        val container = DockerContainer(
            id = "id6",
            names = listOf("/container6"),
            image = "image6",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Order.label to "invalid"
            )
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        assertEquals(Int.MAX_VALUE, result[0].order)
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

    @Test
    fun `test mapping with url label`() = runBlocking {
        val container = DockerContainer(
            id = "id7",
            names = listOf("/container7"),
            image = "image7",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Url.label to "https://custom.url",
                DashLabels.Route.label to "should.be.ignored"
            )
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        assertEquals("https://custom.url", result[0].url)
    }

    @Test
    fun `test ping field presence`() = runBlocking {
        val container = DockerContainer(
            id = "id8",
            names = listOf("/container8"),
            image = "image8",
            state = "running",
            status = "Up",
            labels = mapOf(DashLabels.Enable.label to "true")
        )
        val apiClient = MockDockerApiClient(listOf(container))
        val service = DockerAppService(apiClient)

        val result = service.getAppData()

        assertEquals(1, result.size)
        assertEquals("", result[0].ping)
    }
}
