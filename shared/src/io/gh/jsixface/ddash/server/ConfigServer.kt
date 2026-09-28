package io.gh.jsixface.ddash.server

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.api.AppService
import io.gh.jsixface.ddash.api.DockerAppService
import io.gh.jsixface.ddash.auth.AuthService
import io.gh.jsixface.ddash.auth.ErrorResponse
import io.gh.jsixface.ddash.docker.ContainerNotFoundException
import io.gh.jsixface.ddash.docker.DockerApiException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/** Everything the HTTP layer needs, built once at startup and shared with the rest of the app. */
class ServerDependencies(
    val appService: AppService,
    val dockerAppService: DockerAppService,
    val auth: AuthService,
)

fun Application.configureServer(deps: ServerDependencies) {
    val logger = Logger.withTag("Server")

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(StatusPages) {
        exception<ContainerNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Container not found"))
        }
        exception<DockerApiException> { call, cause ->
            logger.e(cause) { "Docker API error while handling ${call.request.local.uri}" }
            call.respond(HttpStatusCode.BadGateway, ErrorResponse("Docker API error"))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            logger.e(cause) { "Unhandled error while handling ${call.request.local.uri}" }
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    configureRouting(deps)
}
