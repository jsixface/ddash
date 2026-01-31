package io.gh.jsixface.ddash.server

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.ClientFactory
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import io.gh.jsixface.ddash.server.static.staticFiles
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8

fun Application.configureRouting() {
    val logger = Logger.withTag("Routing")
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

            call.respondBytesWriter(contentType = ContentType.Text.Plain) {
                try {
                    dockerAppService.getLogs(id, timestamps).collect { line ->
                        writeStringUtf8(line)
                        flush()
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error streaming logs for $id" }
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

        post("/api/app/{id}/start") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            dockerAppService.start(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
