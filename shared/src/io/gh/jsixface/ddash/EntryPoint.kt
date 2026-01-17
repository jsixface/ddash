package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.configureServer
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking


fun runApplication(args: Array<String>) {
    val logger = Logger.withTag("EntryPoint")
    logger.i { "Starting Docker Dashboard application..." }

    val settings = Globals.settings
    val dockerClient = ClientFactory.getDockerClient()
    val apiClient = UnixSocketDockerApiClient(dockerClient)
    val startupCoordinator = StartupCoordinator(apiClient)

    runBlocking {
        startupCoordinator.run()
    }

    val server = embeddedServer(
        CIO,
        port = settings.port,
        host = settings.host,
        module = Application::configureServer
    )

    setupShutdownHook {
        logger.i { "Shutting down Docker Dashboard..." }
        server.stop(1000, 5000)
        startupCoordinator.stopMonitoring()
    }

    server.start(wait = true)
}
