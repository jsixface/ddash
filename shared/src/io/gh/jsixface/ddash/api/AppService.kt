package io.gh.jsixface.ddash.api

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ExternalConfigService
import io.gh.jsixface.ddash.caddy.CaddyApi

class AppService(
    private val dockerAppService: DockerAppService,
    private val externalConfigService: ExternalConfigService,
    private val caddyApi: CaddyApi
) {
    private val logger = Logger.withTag("AppService")

    suspend fun getAllAppData(): List<AppData> {
        val dockerApps = dockerAppService.getAppData()
        val externalApps = externalConfigService.getExternalApps()
        val caddyRoutes = try {
            caddyApi.getRoutes()
        } catch (e: Exception) {
            logger.e(e) { "Error fetching routes from Caddy" }
            emptyList()
        }

        val managedUrls = (dockerApps + externalApps).map { it.url.removePrefix("http://").removePrefix("https://") }.toSet()

        val unmanagedApps = caddyRoutes
            .filterNot { route -> managedUrls.any { it == route } }
            .distinct()
            .map { route ->
                AppData(
                    id = "caddy-$route",
                    name = formatRouteToName(route),
                    url = "http://$route", // Default to http, can be improved if we know it's secure
                    category = "Unmanaged Apps",
                    status = AppStatus.EXTERNAL,
                    icon = "LayoutGrid"
                )
            }

        // Deduplicate by URL/hostname, preferring managed apps
        val allApps = (dockerApps + externalApps + unmanagedApps)
        val seenUrls = mutableSetOf<String>()
        val result = mutableListOf<AppData>()

        for (app in allApps) {
            val host = app.url.removePrefix("http://").removePrefix("https://")
            if (host.isNotEmpty() && seenUrls.contains(host)) {
                continue
            }
            if (host.isNotEmpty()) {
                seenUrls.add(host)
            }
            result.add(app)
        }

        return result.sortedWith(compareBy({ it.category }, { it.order }, { it.name }))
    }

    private fun formatRouteToName(route: String): String {
        val parts = route.split(".")
        val nameParts = if (parts.size > 1) parts.dropLast(1) else parts
        return nameParts.joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
