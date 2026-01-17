package io.gh.jsixface.ddash.docker

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.Globals
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.unixSocket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class UnixSocketDockerApiClient(private val client: HttpClient) : DockerApiClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = Logger.withTag("UnixSocketDockerApiClient")

    override suspend fun listImages(): List<DockerImage> = client.get("/images/json").body()

    override suspend fun listContainers(): List<DockerContainer> = client.get("/containers/json").body()

    override suspend fun ping(): Boolean {
        return try {
            client.get("/_ping").status.value == 200
        } catch (e: Exception) {
            false
        }
    }

    override fun events(): Flow<DockerEvent> = flow {
        client.prepareGet("/events") {
            unixSocket(Globals.settings.dockerSocket)
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
        }.execute { response ->
            val channel: ByteReadChannel = response.body()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isNotEmpty()) {
                    try {
                        val event = json.decodeFromString<DockerEvent>(line)
                        emit(event)
                    } catch (e: Exception) {
                        logger.e { "Error decoding Docker event: $line. Reason: ${e.message}" }
                    }
                }
            }
        }
    }
}
