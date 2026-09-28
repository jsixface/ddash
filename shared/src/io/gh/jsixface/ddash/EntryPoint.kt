package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.gh.jsixface.ddash.api.AppService
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.auth.AuthService
import io.gh.jsixface.ddash.auth.OidcClient
import io.gh.jsixface.ddash.caddy.HttpCaddyApi
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.ServerDependencies
import io.gh.jsixface.ddash.server.configureServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


fun runApplication(args: Array<String>) {
    if (args.contains("--debug")) {
        Logger.setMinSeverity(Severity.Debug)
    }
    val logger = Logger.withTag("EntryPoint")
    logger.i { "Starting Docker Dashboard application..." }

    val settings = Globals.settings

    // One set of clients and services shared by the route manager and the HTTP layer.
    val dockerHttp = ClientFactory.getDockerClient()
    val caddyHttp = ClientFactory.getCaddyClient()
    val plainHttp = ClientFactory.getPlainClient()
    val apiClient = UnixSocketDockerApiClient(dockerHttp)
    val caddyApi = HttpCaddyApi(caddyHttp)

    val dockerAppService = DockerAppService(apiClient)
    val auth = AuthService(settings.oidc, settings.oidc?.let { OidcClient(it, plainHttp) })
    if (auth.enabled) {
        logger.i { "OIDC login enabled (issuer ${settings.oidc?.issuerUrl}). Anonymous users can view the dashboard only." }
    } else {
        logger.w { "OIDC is not configured: anyone who can reach DDash can start, stop and restart containers." }
    }
    val deps = ServerDependencies(
        appService = AppService(dockerAppService, ExternalConfigService(), caddyApi),
        dockerAppService = dockerAppService,
        auth = auth,
    )

    // Docker/Caddy may come up after us; the coordinator keeps retrying in the background so the dashboard is
    // available immediately.
    val startupCoordinator = StartupCoordinator(apiClient, caddyApi)
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    backgroundScope.launch { startupCoordinator.run() }

    val server = embeddedServer(
        CIO,
        port = settings.port,
        host = settings.host,
    ) {
        configureServer(deps)
    }

    setupShutdownHook {
        logger.i { "Shutting down Docker Dashboard..." }
        server.stop(1000, 5000)
        startupCoordinator.stopMonitoring()
        backgroundScope.cancel()
        dockerHttp.close()
        caddyHttp.close()
        plainHttp.close()
    }

    server.start(wait = true)
}
