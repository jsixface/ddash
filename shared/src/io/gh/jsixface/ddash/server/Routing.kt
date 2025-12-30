package io.gh.jsixface.ddash.server

import io.gh.jsixface.ddash.ClientFactory
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.static.staticFiles
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val dockerClient = ClientFactory.getDockerClient()
    val apiClient = UnixSocketDockerApiClient(dockerClient)
    val dockerAppService = DockerAppService(apiClient)

    routing {
        staticFiles {
            rootPath = "web/dist"
        }

        get("/api/apps") {
            call.respond(dockerAppService.getAppData())
        }
    }
}
