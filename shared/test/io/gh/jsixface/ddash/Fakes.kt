package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.caddy.RoutePlacement
import io.gh.jsixface.ddash.docker.ContainerNotFoundException
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.gh.jsixface.ddash.docker.def.DockerPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

fun managedContainer(
    id: String,
    name: String,
    route: String? = null,
    port: Int = 80,
    extraLabels: Map<String, String> = emptyMap(),
) = DockerContainer(
    id = id,
    names = listOf("/$name"),
    image = "img-$name",
    state = "running",
    status = "Up",
    labels = buildMap {
        put(DashLabels.Enable.label, "true")
        route?.let { put(DashLabels.Route.label, it) }
        putAll(extraLabels)
    },
    ports = listOf(DockerPort(privatePort = port, type = "tcp")),
)

class FakeDockerApiClient(var containers: List<DockerContainer> = emptyList()) : DockerApiClient {
    val actions = mutableListOf<String>()
    var logLines: List<String> = listOf("line 1\n")

    override suspend fun listImages(): List<DockerImage> = emptyList()
    override suspend fun listContainers(): List<DockerContainer> = containers
    override suspend fun ping(): Boolean = true
    override fun events(): Flow<DockerEvent> = emptyFlow()
    override fun containerLogs(containerId: String, tail: Int, follow: Boolean, timestamps: Boolean): Flow<String> =
        flowOf(*logLines.toTypedArray())

    private fun act(action: String, id: String) {
        if (containers.none { it.id == id }) throw ContainerNotFoundException(id)
        actions += "$action:$id"
    }

    override suspend fun stopContainer(containerId: String) = act("stop", containerId)
    override suspend fun restartContainer(containerId: String) = act("restart", containerId)
    override suspend fun startContainer(containerId: String) = act("start", containerId)
}

/** In-memory Caddy that models real index semantics: `addRoute` inserts at index 0, `removeRoute` deletes by index. */
class FakeCaddyApi(
    private val servers: Map<String, List<String>> = mapOf("srv80" to listOf(":80"), "srv443" to listOf(":443")),
) : CaddyApi {
    /** serverId -> routes, each route being the list of hosts it matches. */
    val routes: MutableMap<String, MutableList<List<String>>> = servers.keys.associateWith { mutableListOf<List<String>>() }.toMutableMap()
    val upstreams = mutableMapOf<String, String>()
    var failReads = false
    var failWrites = false
    var saved = 0

    fun hostsOn(serverId: String): List<List<String>> = routes.getValue(serverId)

    override suspend fun checkConnectivity(): Boolean = true
    override suspend fun getRoutes(): List<String> = routes.values.flatten().flatten()
    override suspend fun getRoutePlacements(): List<RoutePlacement> {
        if (failReads) error("caddy unreachable")
        return routes.flatMap { (serverId, list) ->
            list.flatMapIndexed { index, hosts ->
                // Only single-host routes with a known upstream look like the plain reverse_proxy routes DDash creates.
                val upstream = hosts.singleOrNull()?.let { upstreams[it] }
                hosts.map { RoutePlacement(it, serverId, index, hosts, upstream = upstream) }
            }
        }
    }

    override suspend fun resolveServerId(secure: Boolean): String? =
        servers.entries.find { it.value.contains(if (secure) ":443" else ":80") }?.key

    override suspend fun addRoute(host: String, upstream: String, serverId: String) {
        if (failWrites) error("caddy write failed")
        routes.getValue(serverId).add(0, listOf(host))
        upstreams[host] = upstream
    }

    override suspend fun removeRoute(serverId: String, index: Int) {
        if (failWrites) error("caddy write failed")
        routes.getValue(serverId).removeAt(index)
    }

    val updates = mutableListOf<Pair<String, String>>()

    override suspend fun updateRoute(placement: RoutePlacement, upstream: String) {
        if (failWrites) error("caddy write failed")
        updates += placement.host to upstream
        upstreams[placement.host] = upstream
    }

    override suspend fun saveConfig() {
        saved++
    }
}
