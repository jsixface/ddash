package io.gh.jsixface.ddash.caddy

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * Talks to the Caddy admin API. Read and write operations throw when Caddy is unreachable or answers with an error
 * status, so callers can tell "Caddy failed" apart from "there are no routes".
 */
interface CaddyApi {
    suspend fun checkConnectivity(): Boolean
    suspend fun getRoutes(): List<String>
    suspend fun getRoutePlacements(): List<RoutePlacement>
    suspend fun resolveServerId(secure: Boolean): String?
    suspend fun addRoute(host: String, upstream: String, serverId: String)
    suspend fun removeRoute(serverId: String, index: Int)
    suspend fun saveConfig()
}

class HttpCaddyApi(private val client: HttpClient = ClientFactory.getCaddyClient()) : CaddyApi {

    private val logger = Logger.withTag("CaddyApi")

    override suspend fun checkConnectivity(): Boolean {
        return try {
            client.get("/config/") { expectSuccess = false }.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Caddy connectivity check failed" }
            false
        }
    }

    override suspend fun getRoutes(): List<String> {
        val servers = getServers()
        logger.d { "Found ${servers.size} servers" }
        val routes = servers.values
            .flatMap { it.routes }
            .flatMap { it.match.orEmpty().flatMap { m -> m.host } }
        logger.d { "Found routes = $routes" }
        return routes
    }

    override suspend fun getRoutePlacements(): List<RoutePlacement> {
        val placements = getServers().flatMap { (serverId, server) ->
            server.routes.flatMapIndexed { index, route ->
                val hosts = route.match.orEmpty().flatMap { it.host }
                hosts.map { host -> RoutePlacement(host, serverId, index, hosts) }
            }
        }
        logger.d { "Found ${placements.size} route placements" }
        return placements
    }

    override suspend fun resolveServerId(secure: Boolean): String? {
        val targetPort = if (secure) ":443" else ":80"
        val serverId = getServers().entries.find { it.value.listen.contains(targetPort) }?.key
        logger.d { "Target server for port $targetPort is $serverId (secure: $secure)" }
        return serverId
    }

    private suspend fun getServers(): Map<String, CaddyServer> = client.get("/config/apps/http/servers").body()

    override suspend fun addRoute(host: String, upstream: String, serverId: String) {
        logger.i { "Adding route for $host -> $upstream on server $serverId" }
        val route = CaddyRoute(
            match = listOf(CaddyMatcher(host = listOf(host))),
            handle = listOf(CaddyHandler.ReverseProxy(listOf(CaddyUpstream(upstream))))
        )
        // PUT on an array index inserts at that position, shifting later routes down by one.
        client.put("/config/apps/http/servers/$serverId/routes/0") {
            contentType(ContentType.Application.Json)
            setBody(route)
        }
    }

    override suspend fun removeRoute(serverId: String, index: Int) {
        logger.i { "Removing route at index $index from server $serverId" }
        client.delete("/config/apps/http/servers/$serverId/routes/$index")
    }

    override suspend fun saveConfig() {
        logger.i { "Saving Caddy configuration" }
        client.post("/admin/config/save")
    }
}
