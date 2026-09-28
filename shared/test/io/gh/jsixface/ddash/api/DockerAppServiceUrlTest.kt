package io.gh.jsixface.ddash.api

import io.gh.jsixface.ddash.FakeDockerApiClient
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.managedContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class DockerAppServiceUrlTest {
    private suspend fun urlFor(vararg labels: Pair<String, String>): String {
        val docker = FakeDockerApiClient(listOf(managedContainer("c1", "web", extraLabels = labels.toMap())))
        return DockerAppService(docker).getAppData().single().url
    }

    @Test
    fun `per-container https=false wins over global secure routing so link and route agree`() = runTest {
        // Same rule RouteManager uses to pick the Caddy server: label first, global default otherwise.
        assertEquals("http://a.local", urlFor(DashLabels.Route.label to "a.local", DashLabels.Https.label to "false"))
        assertEquals("https://a.local", urlFor(DashLabels.Route.label to "a.local", DashLabels.Https.label to "true"))
        assertEquals("http://a.local", urlFor(DashLabels.Route.label to "a.local")) // global default is false in tests
    }

    @Test
    fun `explicit http and https urls are kept`() = runTest {
        assertEquals("https://x.example/app", urlFor(DashLabels.Url.label to "https://x.example/app"))
        assertEquals("http://x.example:8080", urlFor(DashLabels.Url.label to " http://x.example:8080 "))
    }

    @Test
    fun `dangerous schemes are dropped and bare values cannot become script urls`() = runTest {
        assertEquals("", urlFor(DashLabels.Url.label to "javascript://%0aalert(1)"))
        assertEquals("", urlFor(DashLabels.Url.label to "data://text/html,x"))
        assertEquals("http://javascript:alert(1)", urlFor(DashLabels.Url.label to "javascript:alert(1)"))
        assertEquals("", urlFor(DashLabels.Url.label to "   "))
    }

    @Test
    fun `docker errors are not hidden`() = runTest {
        val failing = object : io.gh.jsixface.ddash.docker.DockerApiClient by FakeDockerApiClient() {
            override suspend fun listContainers() = throw io.gh.jsixface.ddash.docker.DockerApiException("down")
        }
        kotlin.test.assertFailsWith<io.gh.jsixface.ddash.docker.DockerApiException> {
            DockerAppService(failing).getAppData()
        }
    }
}
