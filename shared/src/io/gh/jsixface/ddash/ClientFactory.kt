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
        }
    }

    fun getCaddyClient() = httpClient {
        defaultRequest {
            url(Globals.settings.caddyAdminUrl)
        }
    }

    /** Plain client (no base URL), e.g. for talking to an OIDC identity provider. */
    fun getPlainClient() = httpClient {}

    private fun httpClient(clientConfig: HttpClientConfig<CIOEngineConfig>.() -> Unit) =
        HttpClient(CIO) {
            // Turn 4xx/5xx responses into exceptions so callers cannot mistake a failed call for a successful one.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                })
            }
            clientConfig()
        }
}
