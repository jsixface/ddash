package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.configureServer
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking


fun runApplication(args: Array<String>) {
    println("Starting Docker Dashboard application...")

    val settings = Globals.settings

    runBlocking {
        val dockerClient = ClientFactory.getDockerClient()
        val apiClient = UnixSocketDockerApiClient(dockerClient)
        StartupCoordinator(apiClient).run()
    }

    embeddedServer(
        CIO,
        port = settings.port,
        host = settings.host,
        module = Application::configureServer
    ).start(wait = true)
}
