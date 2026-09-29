package io.gh.jsixface.ddash.server

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.auth.authRoutes
import io.gh.jsixface.ddash.auth.requireAccess
import io.gh.jsixface.ddash.server.static.staticFiles
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import kotlin.coroutines.cancellation.CancellationException

/**
 * Public endpoints (anyone can use them): the static UI, `/api/apps`, `/api/session`.
 * Privileged endpoints (need a login when OIDC is configured): container logs, start, stop and restart.
 */
fun Application.configureRouting(deps: ServerDependencies) {
    val logger = Logger.withTag("Routing")
    val dockerAppService = deps.dockerAppService
    val auth = deps.auth

    routing {
        staticFiles {
            rootPath = "web/dist"
        }

        authRoutes(auth)

        get("/api/apps") {
            call.respond(deps.appService.getAllAppData())
        }

        get("/api/app/{id}/logs") {
            if (!call.requireAccess(auth)) return@get
            val id = call.containerId() ?: return@get
            val timestamps = call.request.queryParameters["timestamps"]?.toBoolean() ?: false

            // Resolved before the response starts so an unknown container is a clean 404 rather than an empty 200.
            val logs = dockerAppService.getLogs(id, timestamps)

            call.respondBytesWriter(contentType = ContentType.Text.Plain) {
                try {
                    logs.collect { line ->
                        writeStringUtf8(line)
                        flush()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.e(e) { "Error streaming logs for $id" }
                }
            }
        }

        post("/api/app/{id}/stop") {
            if (!call.requireAccess(auth)) return@post
            val id = call.containerId() ?: return@post
            dockerAppService.stop(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/api/app/{id}/restart") {
            if (!call.requireAccess(auth)) return@post
            val id = call.containerId() ?: return@post
            dockerAppService.restart(id)
            call.respond(HttpStatusCode.OK)
        }

        post("/api/app/{id}/start") {
            if (!call.requireAccess(auth)) return@post
            val id = call.containerId() ?: return@post
            dockerAppService.start(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}

/** The `{id}` path parameter, or null after answering 400. */
private suspend fun ApplicationCall.containerId(): String? {
    val id = parameters["id"]
    if (id.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest)
        return null
    }
    return id
}
