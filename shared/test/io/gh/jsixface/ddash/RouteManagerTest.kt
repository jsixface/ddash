package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.docker.DashLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RouteManagerTest {

    @Test
    fun `removing a misplaced route uses fresh indexes after earlier additions`() = runTest {
        // Two misplaced routes on srv443 (indexes 0 and 1) and a bystander at index 2 that must survive.
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv443").addAll(listOf(listOf("a.local"), listOf("b.local"), listOf("bystander.local")))
        val docker = FakeDockerApiClient(
            listOf(
                managedContainer("1", "a", route = "a.local"),
                managedContainer("2", "b", route = "b.local"),
            )
        )

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf(listOf("bystander.local")), caddy.hostsOn("srv443"))
        assertEquals(setOf(listOf("a.local"), listOf("b.local")), caddy.hostsOn("srv80").toSet())
    }

    @Test
    fun `route shared with other hosts is not removed`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv443").add(listOf("a.local", "other.local"))
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf(listOf("a.local", "other.local")), caddy.hostsOn("srv443"))
        assertEquals(listOf(listOf("a.local")), caddy.hostsOn("srv80"))
    }

    @Test
    fun `nothing is changed when Caddy cannot be read`() = runTest {
        val caddy = FakeCaddyApi().apply { failReads = true }
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertTrue(caddy.hostsOn("srv80").isEmpty())
        assertTrue(caddy.hostsOn("srv443").isEmpty())
    }

    @Test
    fun `a failure for one container does not stop the others`() = runTest {
        val caddy = FakeCaddyApi()
        val docker = FakeDockerApiClient(
            listOf(
                // No exposed port and no label -> skipped, not an exception.
                managedContainer("1", "a", route = "a.local").copy(ports = null),
                managedContainer("2", "b", route = "b.local"),
            )
        )

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf(listOf("b.local")), caddy.hostsOn("srv80"))
    }

    @Test
    fun `a write failure is contained and config is not saved`() = runTest {
        val caddy = FakeCaddyApi().apply { failWrites = true }
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertEquals(0, caddy.saved)
    }

    @Test
    fun `only ddash's own image is routed via localhost`() = runTest {
        val caddy = FakeCaddyApi()
        val docker = FakeDockerApiClient(
            listOf(
                managedContainer("1", "dash", route = "dash.local").copy(image = "ghcr.io/jsixface/ddash:latest"),
                managedContainer("2", "other", route = "other.local").copy(image = "example/ddash-exporter:1"),
            )
        )

        RouteManager(docker, caddy).processContainers()

        assertEquals("localhost:${Globals.settings.port}", caddy.upstreams["dash.local"])
        assertEquals("other:80", caddy.upstreams["other.local"])
    }

    @Test
    fun `https label is honoured for placement`() = runTest {
        val caddy = FakeCaddyApi()
        val docker = FakeDockerApiClient(
            listOf(managedContainer("1", "a", route = "a.local", extraLabels = mapOf(DashLabels.Https.label to "true")))
        )

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf(listOf("a.local")), caddy.hostsOn("srv443"))
    }
}
