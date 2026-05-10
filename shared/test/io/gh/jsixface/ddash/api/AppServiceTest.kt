package io.gh.jsixface.ddash.api

import io.gh.jsixface.ddash.ExternalConfigService
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppServiceTest {

    private class MockCaddyApi(val routes: List<String>) : CaddyApi {
        override suspend fun checkConnectivity(): Boolean = true
        override suspend fun getRoutes(): List<String> = routes
        override suspend fun addRoute(host: String, upstream: String) {}
        override suspend fun saveConfig() {}
    }

    private class MockExternalConfigService(val apps: List<AppData>) : ExternalConfigService() {
        override fun getExternalApps(): List<AppData> = apps
    }

    private class MockDockerAppService : DockerAppService(
        object : DockerApiClient {
            override suspend fun ping() = true
            override suspend fun listContainers() = emptyList<DockerContainer>()
            override suspend fun listImages(): List<DockerImage> = emptyList()
            override fun events(): Flow<DockerEvent> = emptyFlow()
            override fun containerLogs(containerId: String, tail: Int, follow: Boolean, timestamps: Boolean): Flow<String> = emptyFlow()
            override suspend fun stopContainer(id: String) {}
            override suspend fun restartContainer(id: String) {}
            override suspend fun startContainer(id: String) {}
        }
    ) {
        var mockApps: List<AppData> = emptyList()
        override suspend fun getAppData(): List<AppData> = mockApps
    }

    @Test
    fun testUnmanagedAppsNaming() = runTest {
        val caddyApi = MockCaddyApi(listOf("stuff.home.local", "blog.example.com", "dash"))
        val dockerAppService = MockDockerAppService()
        val appService = AppService(dockerAppService, MockExternalConfigService(emptyList()), caddyApi)

        val apps = appService.getAllAppData()

        val stuffApp = apps.find { it.url == "http://stuff.home.local" }
        assertEquals("Stuff Home", stuffApp?.name)
        assertEquals("Unmanaged Apps", stuffApp?.category)

        val blogApp = apps.find { it.url == "http://blog.example.com" }
        assertEquals("Blog Example", blogApp?.name)

        val dashApp = apps.find { it.url == "http://dash" }
        assertEquals("Dash", dashApp?.name)
    }

    @Test
    fun testDeduplication() = runTest {
        val dockerApps = listOf(
            AppData("docker-1", "Docker App", "http://managed.local", "Category 1", AppStatus.RUNNING)
        )
        val externalApps = listOf(
            AppData("ext-1", "Ext App", "https://ext.local", "Category 2", AppStatus.EXTERNAL)
        )
        val caddyRoutes = listOf("managed.local", "ext.local", "unmanaged.local")

        val dockerAppService = MockDockerAppService().apply { mockApps = dockerApps }
        val appService = AppService(dockerAppService, MockExternalConfigService(externalApps), MockCaddyApi(caddyRoutes))

        val apps = appService.getAllAppData()

        assertEquals(3, apps.size)
        assertTrue(apps.any { it.name == "Docker App" }, "Should contain Docker App")
        assertTrue(apps.any { it.name == "Ext App" }, "Should contain Ext App")
        assertTrue(apps.any { it.name == "Unmanaged" }, "Should contain Unmanaged app")

        // Verify no duplicates for managed.local and ext.local
        assertEquals(1, apps.count { it.url.removePrefix("http://").removePrefix("https://") == "managed.local" })
        assertEquals(1, apps.count { it.url.removePrefix("http://").removePrefix("https://") == "ext.local" })
    }
}
