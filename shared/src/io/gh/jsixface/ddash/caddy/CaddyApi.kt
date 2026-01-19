package io.gh.jsixface.ddash.caddy

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ClientFactory
import io.gh.jsixface.ddash.Globals
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface CaddyApi {
    suspend fun checkConnectivity(): Boolean
    suspend fun getRoutes(): List<String>
    suspend fun addRoute(host: String, upstream: String)
    suspend fun saveConfig()
}

class HttpCaddyApi(private val client: HttpClient = ClientFactory.getCaddyClient()) : CaddyApi {

    private val logger = Logger.withTag("CaddyApi")

    override suspend fun checkConnectivity(): Boolean {
        return try {
            client.get("/config/").status.value in 200..299
        } catch (e: Exception) {
            logger.e(e) { "Caddy connectivity check failed" }
            false
        }
    }

    override suspend fun getRoutes(): List<String> {
        return try {
            val servers: Map<String, CaddyServer> = client.get("/config/apps/http/servers").body()
            logger.d { "Found ${servers.size} servers" }
            val routes = servers.values
                .flatMap { it.routes }
                .flatMap { it.match?.map { m -> m.host } ?: emptyList() }
                .flatten()
            logger.d { "Found routes = $routes" }
            routes
        } catch (e: Exception) {
            logger.e(e) { "Error fetching routes from Caddy" }
            emptyList()
        }
    }

    override suspend fun addRoute(host: String, upstream: String) {
        logger.i { "Adding route for $host -> $upstream" }
        val targetServer = try {
            val servers: Map<String, CaddyServer> = client.get("/config/apps/http/servers").body()
            val useSecure = Globals.settings.caddySecureRouting
            val targetPort = if (useSecure) ":443" else ":80"
            val serverId = servers.entries.find { it.value.listen.contains(targetPort) }?.key ?: "srv0"
            logger.d { "Target server for port $targetPort is $serverId (secure: $useSecure)" }
            serverId
        } catch (e: Exception) {
            logger.e(e) { "Error determining target server, defaulting to srv0" }
            "srv0"
        }

        val route = CaddyRoute(
            match = listOf(CaddyMatcher(host = listOf(host))),
            handle = listOf(CaddyHandler.ReverseProxy(listOf(CaddyUpstream(upstream))))
        )
        try {
            client.post("/config/apps/http/servers/$targetServer/routes") {
                contentType(ContentType.Application.Json)
                setBody(route)
            }
        } catch (e: Exception) {
            logger.e(e) { "Error adding route to Caddy" }
        }
    }

    override suspend fun saveConfig() {
        logger.i { "Saving Caddy configuration" }
        try {
            client.post("/admin/config/save")
        } catch (e: Exception) {
            logger.e(e) { "Error saving Caddy configuration" }
        }
    }
}
