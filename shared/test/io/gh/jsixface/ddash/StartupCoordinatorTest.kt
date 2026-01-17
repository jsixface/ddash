package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.gh.jsixface.ddash.docker.def.DockerPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupCoordinatorTest {

    class MockDockerApiClient(private val containers: List<DockerContainer>) : DockerApiClient {
        override suspend fun listImages(): List<DockerImage> = emptyList()
        override suspend fun listContainers(): List<DockerContainer> = containers
        override suspend fun ping(): Boolean = true
        override fun events(): Flow<DockerEvent> = emptyFlow()
    }

    class MockCaddyApi : CaddyApi {
        val addedRoutes = mutableListOf<Pair<String, String>>()
        override suspend fun checkConnectivity(): Boolean = true
        override suspend fun getRoutes(): List<String> = emptyList()
        override suspend fun addRoute(host: String, upstream: String) {
            addedRoutes.add(host to upstream)
        }
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
}
