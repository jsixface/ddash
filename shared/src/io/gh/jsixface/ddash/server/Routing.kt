package io.gh.jsixface.ddash.server

import io.gh.jsixface.ddash.ClientFactory
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.static.staticFiles
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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

        get("/api/app/{id}/logs") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val timestamps = call.request.queryParameters["timestamps"]?.toBoolean() ?: false

            call.respondTextWriter(contentType = ContentType.Text.Plain) {
                dockerAppService.getLogs(id, timestamps).collect { line ->
                    write(line)
                    flush()
                }
            }
        }

        post("/api/app/{id}/stop") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            dockerAppService.stop(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/api/app/{id}/restart") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            dockerAppService.restart(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
