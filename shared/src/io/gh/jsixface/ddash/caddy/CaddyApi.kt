package io.gh.jsixface.ddash.caddy

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ClientFactory
import io.gh.jsixface.ddash.Globals
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class CaddyApi(private val client: HttpClient = ClientFactory.getCaddyClient()) {

    private val logger = Logger.withTag("CaddyApi")
    suspend fun getRoutes(): List<CaddyRoute> {
        val servers: CaddyServers = client.get(
            Globals.settings.caddyAdminUrl + "/config/apps/http"
        ).body()
        logger.d { "Found ${servers.servers.size} servers" }
        val routes = servers.servers.values
            .flatMap { it.routes }
            .flatMap { it.match?.map { m -> m.host } ?: emptyList()  }
            .flatten()
        logger.d { "Found routes = $routes" }
        return emptyList()
    }
}
