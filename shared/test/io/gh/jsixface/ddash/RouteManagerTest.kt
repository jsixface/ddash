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

    @Test
    fun `existing route with a stale upstream is updated in place`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv80").addAll(listOf(listOf("other.local"), listOf("a.local"), listOf("z.local")))
        caddy.upstreams["a.local"] = "a:80"
        // The container now exposes 8080 via the label.
        val docker = FakeDockerApiClient(
            listOf(managedContainer("1", "a", route = "a.local", extraLabels = mapOf(DashLabels.Port.label to "8080")))
        )

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf("a.local" to "a:8080"), caddy.updates)
        // Same position, nothing added or removed.
        assertEquals(listOf(listOf("other.local"), listOf("a.local"), listOf("z.local")), caddy.hostsOn("srv80"))
    }

    @Test
    fun `route already pointing at the right upstream is left alone`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv80").add(listOf("a.local"))
        caddy.upstreams["a.local"] = "a:80"
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertTrue(caddy.updates.isEmpty())
        assertEquals(0, caddy.saved)
    }

    @Test
    fun `routes that are not the plain shape DDash creates are never rewritten`() = runTest {
        val caddy = FakeCaddyApi()
        // Multi-host route, and a single-host route with no plain upstream (hand-written config).
        caddy.routes.getValue("srv80").addAll(listOf(listOf("a.local", "b.local"), listOf("c.local")))
        caddy.upstreams["a.local"] = "old:1"
        val docker = FakeDockerApiClient(
            listOf(
                managedContainer("1", "a", route = "a.local"),
                managedContainer("3", "c", route = "c.local"),
            )
        )

        RouteManager(docker, caddy).processContainers()

        assertTrue(caddy.updates.isEmpty())
        assertEquals(listOf(listOf("a.local", "b.local"), listOf("c.local")), caddy.hostsOn("srv80"))
    }

    @Test
    fun `a container whose upstream cannot be determined keeps its existing route`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv80").add(listOf("a.local"))
        caddy.upstreams["a.local"] = "a:80"
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local").copy(ports = null)))

        RouteManager(docker, caddy).processContainers()

        assertTrue(caddy.updates.isEmpty())
    }

    @Test
    fun `containers claiming the same host do not fight over it`() = runTest {
        val caddy = FakeCaddyApi()
        val docker = FakeDockerApiClient(
            listOf(
                managedContainer("2", "zeta", route = "app.local", port = 9000),
                managedContainer("1", "alpha", route = "app.local", port = 8000),
            )
        )
        val manager = RouteManager(docker, caddy)

        manager.processContainers()
        manager.processContainers()
        manager.processContainers()

        // "alpha" wins deterministically (by name); the route is created once and never flips afterwards.
        assertEquals(listOf(listOf("app.local")), caddy.hostsOn("srv80"))
        assertEquals("alpha:8000", caddy.upstreams["app.local"])
        assertTrue(caddy.updates.isEmpty())
    }

    @Test
    fun `stale upstream update saves the config when enabled`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv80").add(listOf("a.local"))
        caddy.upstreams["a.local"] = "old:1"
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertEquals(listOf("a.local" to "a:80"), caddy.updates)
        assertEquals(if (Globals.settings.caddyAutoSaveConfig) 1 else 0, caddy.saved)
    }

    @Test
    fun `routes of removed containers are not cleaned up`() = runTest {
        val caddy = FakeCaddyApi()
        caddy.routes.getValue("srv80").add(listOf("gone.local"))
        caddy.upstreams["gone.local"] = "gone:80"
        val docker = FakeDockerApiClient(listOf(managedContainer("1", "a", route = "a.local")))

        RouteManager(docker, caddy).processContainers()

        assertTrue(listOf("gone.local") in caddy.hostsOn("srv80"))
    }
}
