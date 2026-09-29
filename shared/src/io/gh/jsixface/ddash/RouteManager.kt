package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.caddy.CaddyApi
import io.gh.jsixface.ddash.caddy.RoutePlacement
import io.gh.jsixface.ddash.docker.DashLabels
import io.gh.jsixface.ddash.docker.DockerApiClient
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.isHttps
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RouteManager(
    private val dockerClient: DockerApiClient,
    private val caddyApi: CaddyApi,
) {
    private val logger = Logger.withTag("RouteManager")
    private val settings = Globals.settings

    // Startup and Docker events can both trigger a pass; interleaved passes would act on stale route indexes.
    private val processLock = Mutex()

    suspend fun processContainers() = processLock.withLock {
        val containers = fetchContainers() ?: return@withLock

        val appsToRoute = containers.filter { container ->
            container.labels[DashLabels.Enable.label]?.toBoolean() == true &&
                container.labels.containsKey(DashLabels.Route.label)
        }

        if (appsToRoute.isEmpty()) {
            logger.i { "No containers found with ddash.route label." }
            return@withLock
        }

        // One route per host. If several containers claim the same host they would otherwise keep rewriting each
        // other's upstream, so pick one deterministically and say so.
        val claims = appsToRoute.groupBy { it.labels[DashLabels.Route.label]!! }
        val routed = claims.map { (host, claimants) ->
            val ordered = claimants.sortedBy { it.names.firstOrNull() ?: it.id }
            if (ordered.size > 1) {
                logger.w {
                    "Host $host is claimed by ${ordered.map { it.names.firstOrNull() ?: it.id }}; " +
                        "routing it to ${ordered.first().names.firstOrNull() ?: ordered.first().id}."
                }
            }
            ordered.first()
        }

        // If Caddy can't be read we must not carry on: an empty answer would look like "no routes exist" and lead to
        // duplicate routes.
        var placements = fetchRoutePlacements() ?: return@withLock
        var changed = false

        for (container in routed) {
            val host = container.labels[DashLabels.Route.label]!!
            try {
                if (reconcile(container, host, containers, placements)) {
                    changed = true
                    // Adding a route shifts the indexes of the server's existing routes, so re-read them.
                    placements = fetchRoutePlacements() ?: break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.e(e) { "Failed to reconcile route for $host" }
            }
        }

        if (changed && settings.caddyAutoSaveConfig) {
            try {
                caddyApi.saveConfig()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.w(e) { "Failed to save Caddy configuration" }
            }
        }
    }

    /** Makes sure [host] is routed on the right Caddy server. Returns true if Caddy's config was changed. */
    private suspend fun reconcile(
        container: DockerContainer,
        host: String,
        containers: List<DockerContainer>,
        placements: List<RoutePlacement>,
    ): Boolean {
        logger.d { "Checking container --- ${container.names}, ${container.image}, ${container.ports}" }

        val expectedSecure = container.labels.isHttps(settings.caddySecureRouting)
        val expectedServer = caddyApi.resolveServerId(expectedSecure)
        if (expectedServer == null) {
            logger.e { "No Caddy server listening on ${if (expectedSecure) ":443" else ":80"} found. Skipping route for $host." }
            return false
        }

        val hostPlacements = placements.filter { it.host == host }
        val correctPlacement = hostPlacements.find { it.serverId == expectedServer }
        val upstream = getUpstream(container, containers) ?: return false

        if (correctPlacement != null) return syncUpstream(correctPlacement, host, upstream)

        // Add the new route first so the host is never left without a route if a later step fails. The stale routes
        // are on other servers, so adding here does not shift their indexes.
        caddyApi.addRoute(host, upstream, expectedServer)

        if (hostPlacements.isNotEmpty()) {
            logger.i { "Route for $host is on the wrong server(s); corrected placement to $expectedServer." }
            hostPlacements
                .filter { placement ->
                    (placement.hosts.size <= 1).also { removable ->
                        if (!removable) {
                            logger.w {
                                "Route for $host on ${placement.serverId} also serves ${placement.hosts - host}; " +
                                    "leaving it in place."
                            }
                        }
                    }
                }
                .sortedByDescending { it.index } // highest index first so earlier removals don't shift later ones
                .forEach { caddyApi.removeRoute(it.serverId, it.index) }
        }
        return true
    }

    private suspend fun fetchContainers(): List<DockerContainer>? {
        return try {
            dockerClient.listContainers()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to list containers" }
            null
        }
    }

    private suspend fun fetchRoutePlacements(): List<RoutePlacement>? {
        return try {
            caddyApi.getRoutePlacements()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Error fetching current route placements from Caddy" }
            null
        }
    }

    private fun getUpstream(container: DockerContainer, allContainers: List<DockerContainer>): String? {
        val containerName = container.names.firstOrNull()?.removePrefix("/") ?: container.id
        val isDdash = isDdashContainer(container)

        if (isDdash) {
            return "localhost:${settings.port}"
        }

        val networkMode = container.hostConfig?.networkMode
        val (finalHost, finalPort) = when {
            networkMode == "host" -> {
                val port = container.labels[DashLabels.Port.label]
                if (port == null) {
                    logger.e { "Network mode is 'host' but no ddash.port label found for $containerName. Skipping." }
                    return null
                }
                "host.docker.internal" to port
            }

            networkMode?.startsWith("container:") == true -> {
                val target = networkMode.removePrefix("container:")
                val targetContainerName = allContainers.find { it.id == target || it.id.startsWith(target) }
                    ?.names?.firstOrNull()?.removePrefix("/") ?: target

                val port = container.labels[DashLabels.Port.label]
                if (port == null) {
                    logger.e { "Network mode is attached to '$target' but no ddash.port label found for $containerName. Skipping." }
                    return null
                }
                targetContainerName to port
            }

            else -> {
                val port = getContainerPort(container)
                if (port == null) {
                    logger.e { "Could not determine port for container $containerName. Skipping route." }
                    return null
                }
                containerName to port
            }
        }
        return "$finalHost:$finalPort"
    }

    /**
     * The route exists on the right server; make sure it still points where the container is now (changed
     * `ddash.port`, renamed container, different network mode, ...). Only routes of the plain shape DDash creates
     * are rewritten; anything else is somebody's hand-written config and is left alone.
     */
    private suspend fun syncUpstream(placement: RoutePlacement, host: String, upstream: String): Boolean {
        if (placement.upstream == upstream) {
            logger.d { "Route for $host already exists on the correct server (${placement.serverId})." }
            return false
        }
        if (!placement.replaceable) {
            logger.w {
                "Route for $host on ${placement.serverId} is not a plain single-host reverse_proxy route, so DDash " +
                    "will not change it (expected upstream $upstream)."
            }
            return false
        }
        caddyApi.updateRoute(placement, upstream)
        return true
    }

    /** DDash's own container is reached over localhost (it shares Caddy's network namespace). */
    private fun isDdashContainer(container: DockerContainer): Boolean {
        val name = container.labels[DashLabels.Name.label]
        if (name.equals("D-Dash", ignoreCase = true) || name.equals("DDash", ignoreCase = true)) return true
        // Match the image repository name exactly (ghcr.io/jsixface/ddash:latest -> "ddash"), not any image that
        // merely contains the word.
        val repository = container.image.substringBefore('@').substringAfterLast('/').substringBefore(':')
        return repository.equals("ddash", ignoreCase = true)
    }

    private fun getContainerPort(container: DockerContainer): String? {
        val labelPort = container.labels[DashLabels.Port.label]
        if (labelPort != null) return labelPort

        val ports = container.ports?.map { it.privatePort }?.toSet() ?: emptySet()
        if (ports.isEmpty()) {
            logger.e { "No ports exposed and no ddash.port label found for container ${container.names}" }
            return null
        }

        if (ports.size > 1) {
            logger.e { "Multiple ports exposed and no ddash.port label found for container ${container.names}. Ports: $ports" }
            return null
        }

        return ports.first().toString()
    }
}
