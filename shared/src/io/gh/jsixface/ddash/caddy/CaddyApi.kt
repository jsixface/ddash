package io.gh.jsixface.ddash.caddy

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

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
            client.get("/config/").status.value in 200..299
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Caddy connectivity check failed" }
            false
        }
    }

    override suspend fun getRoutes(): List<String> {
        return try {
            val servers = getServers()
            logger.d { "Found ${servers.size} servers" }
            val routes = servers.values
                .flatMap { it.routes }
                .flatMap { it.match?.map { m -> m.host } ?: emptyList() }
                .flatten()
            logger.d { "Found routes = $routes" }
            routes
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error fetching routes from Caddy" }
            emptyList()
        }
    }

    override suspend fun getRoutePlacements(): List<RoutePlacement> {
        return try {
            val placements = getServers().flatMap { (serverId, server) ->
                server.routes.flatMapIndexed { index, route ->
                    route.match?.flatMap { it.host }?.map { host -> RoutePlacement(host, serverId, index) }
                        ?: emptyList()
                }
            }
            logger.d { "Found ${placements.size} route placements" }
            placements
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error fetching route placements from Caddy" }
            emptyList()
        }
    }

    override suspend fun resolveServerId(secure: Boolean): String? {
        return try {
            val targetPort = if (secure) ":443" else ":80"
            val serverId = getServers().entries.find { it.value.listen.contains(targetPort) }?.key
            logger.d { "Target server for port $targetPort is $serverId (secure: $secure)" }
            serverId
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error determining target server for port ${if (secure) ":443" else ":80"}" }
            null
        }
    }

    private suspend fun getServers(): Map<String, CaddyServer> = client.get("/config/apps/http/servers").body()

    override suspend fun addRoute(host: String, upstream: String, serverId: String) {
        logger.i { "Adding route for $host -> $upstream on server $serverId" }
        val route = CaddyRoute(
            match = listOf(CaddyMatcher(host = listOf(host))),
            handle = listOf(CaddyHandler.ReverseProxy(listOf(CaddyUpstream(upstream))))
        )
        try {
            client.post("/config/apps/http/servers/$serverId/routes") {
                contentType(ContentType.Application.Json)
                setBody(route)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error adding route to Caddy" }
        }
    }

    override suspend fun removeRoute(serverId: String, index: Int) {
        logger.i { "Removing route at index $index from server $serverId" }
        try {
            client.delete("/config/apps/http/servers/$serverId/routes/$index")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error removing route from Caddy" }
        }
    }

    override suspend fun saveConfig() {
        logger.i { "Saving Caddy configuration" }
        try {
            client.post("/admin/config/save")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error saving Caddy configuration" }
        }
    }
}
