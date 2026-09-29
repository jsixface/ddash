package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.caddy.RoutePlacement
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.gh.jsixface.ddash.docker.def.DockerPort
import io.gh.jsixface.ddash.docker.def.HostConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class StartupCoordinatorTest {

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

    class MockCaddyApi : CaddyApi {
        val addedRoutes = mutableListOf<Triple<String, String, String>>()
        val removedRoutes = mutableListOf<Pair<String, Int>>()
        var placements: List<RoutePlacement> = emptyList()
        override suspend fun checkConnectivity(): Boolean = true
        override suspend fun getRoutes(): List<String> = placements.map { it.host }
        override suspend fun getRoutePlacements(): List<RoutePlacement> = placements
        override suspend fun resolveServerId(secure: Boolean): String? = if (secure) "srv443" else "srv80"
        override suspend fun addRoute(host: String, upstream: String, serverId: String) {
            addedRoutes.add(Triple(host, upstream, serverId))
        }

        override suspend fun removeRoute(serverId: String, index: Int) {
            removedRoutes.add(serverId to index)
        }
        override suspend fun updateRoute(placement: RoutePlacement, upstream: String) {}
        override suspend fun saveConfig() {}
    }

    @Test
    fun `test container with single port works`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            ports = listOf(DockerPort(privatePort = 80, type = "tcp"))
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("web1.local", caddyApi.addedRoutes[0].first)
        assertEquals("web1:80", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `test container with ddash-port label works`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Port.label to "8080"
            ),
            ports = listOf(
                DockerPort(privatePort = 80, type = "tcp"),
                DockerPort(privatePort = 443, type = "tcp")
            )
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("web1:8080", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `test container with multiple ports and no label is skipped`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            ports = listOf(
                DockerPort(privatePort = 80, type = "tcp"),
                DockerPort(privatePort = 443, type = "tcp")
            )
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(0, caddyApi.addedRoutes.size)
    }

    @Test
    fun `test container with no ports and no label is skipped`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            ports = emptyList()
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(0, caddyApi.addedRoutes.size)
    }

    @Test
    fun `test container with host network mode works`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Port.label to "8080"
            ),
            hostConfig = HostConfig(networkMode = "host")
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("host.docker.internal:8080", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `test container with attached network mode works`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Port.label to "8080"
            ),
            hostConfig = HostConfig(networkMode = "container:caddy")
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("caddy:8080", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `test container with attached network mode and no port label is skipped`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            hostConfig = HostConfig(networkMode = "container:caddy")
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(0, caddyApi.addedRoutes.size)
    }

    @Test
    fun `test container with attached long ID network mode uses target container name`() = runBlocking {
        val targetContainerId = "0336356519e82aa6a810c5491ee701bc5d31aa0bede6626582bd06015d59d2f3"
        val targetContainer = DockerContainer(
            id = targetContainerId,
            names = listOf("/caddy-server"),
            image = "caddy",
            state = "running",
            status = "Up"
        )
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Port.label to "8080"
            ),
            hostConfig = HostConfig(networkMode = "container:$targetContainerId")
        )
        val dockerClient = MockDockerApiClient(listOf(targetContainer, container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("caddy-server:8080", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `test container with attached ID network mode falls back to ID if name not found`() = runBlocking {
        val containerId = "some-id"
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Port.label to "8080"
            ),
            hostConfig = HostConfig(networkMode = "container:$containerId")
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("$containerId:8080", caddyApi.addedRoutes[0].second)
    }

    @Test
    fun `route already on the correct server is not re-added`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            ports = listOf(DockerPort(privatePort = 80, type = "tcp"))
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        caddyApi.placements = listOf(RoutePlacement("web1.local", "srv80", 0))
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(0, caddyApi.addedRoutes.size)
        assertEquals(0, caddyApi.removedRoutes.size)
    }

    @Test
    fun `route on the wrong server is corrected`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local"
            ),
            ports = listOf(DockerPort(privatePort = 80, type = "tcp"))
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        caddyApi.placements = listOf(RoutePlacement("web1.local", "srv443", 0))
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.removedRoutes.size)
        assertEquals("srv443" to 0, caddyApi.removedRoutes[0])
        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("web1.local", caddyApi.addedRoutes[0].first)
        assertEquals("web1:80", caddyApi.addedRoutes[0].second)
        assertEquals("srv80", caddyApi.addedRoutes[0].third)
    }

    @Test
    fun `per-route ddash-https label overrides global routing`() = runBlocking {
        val container = DockerContainer(
            id = "id1",
            names = listOf("/web1"),
            image = "nginx",
            state = "running",
            status = "Up",
            labels = mapOf(
                DashLabels.Enable.label to "true",
                DashLabels.Route.label to "web1.local",
                DashLabels.Https.label to "true"
            ),
            ports = listOf(DockerPort(privatePort = 80, type = "tcp"))
        )
        val dockerClient = MockDockerApiClient(listOf(container))
        val caddyApi = MockCaddyApi()
        val coordinator = StartupCoordinator(dockerClient, caddyApi)

        coordinator.run()

        assertEquals(1, caddyApi.addedRoutes.size)
        assertEquals("srv443", caddyApi.addedRoutes[0].third)
    }
}
