package io.gh.jsixface.ddash

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ClientFactory {
    fun getDockerClient() = httpClient {
        defaultRequest {
            unixSocket(Globals.settings.dockerSocket)
            header("Content-Type", "application/json")
        }
    }

    fun getCaddyClient() = httpClient {
        defaultRequest {
            url(Globals.settings.caddyAdminUrl)
        }
    }

    private fun httpClient(clientConfig: HttpClientConfig<CIOEngineConfig>.() -> Unit) =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
            clientConfig()
        }
}
