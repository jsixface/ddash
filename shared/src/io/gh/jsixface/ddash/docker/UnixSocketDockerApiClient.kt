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
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.unixSocket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class UnixSocketDockerApiClient(private val client: HttpClient) : DockerApiClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = Logger.withTag("UnixSocketDockerApiClient")

    override suspend fun listImages(): List<DockerImage> = client.get("/images/json").body()

    override suspend fun listContainers(): List<DockerContainer> = client.get("/containers/json") {
        url {
            parameters.append("all", "true")
        }
    }.body()

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
            url {
                parameters.append(
                    "filters", """
                    {"type":["container"],"event":["start","die","stop","destroy","rename","update"]}
                """.trim()
                )
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

    override fun containerLogs(
        containerId: String,
        tail: Int,
        follow: Boolean,
        timestamps: Boolean,
    ): Flow<String> = flow {
        client.prepareGet("/containers/$containerId/logs") {
            unixSocket(Globals.settings.dockerSocket)
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
            url {
                parameters.append("stdout", "true")
                parameters.append("stderr", "true")
                parameters.append("follow", follow.toString())
                parameters.append("tail", tail.toString())
                parameters.append("timestamps", timestamps.toString())
            }
        }.execute { response ->
            val channel: ByteReadChannel = response.body()
            while (!channel.isClosedForRead) {
                val header = ByteArray(8)
                try {
                    channel.readFully(header)
                } catch (e: Exception) {
                    break
                }

                val size = ((header[4].toInt() and 0xFF) shl 24) or
                    ((header[5].toInt() and 0xFF) shl 16) or
                    ((header[6].toInt() and 0xFF) shl 8) or
                    (header[7].toInt() and 0xFF)

                if (size > 0) {
                    val payload = ByteArray(size)
                    channel.readFully(payload)
                    emit(payload.decodeToString())
                }
            }
        }
    }

    override suspend fun stopContainer(containerId: String) {
        client.post("/containers/$containerId/stop")
    }

    override suspend fun restartContainer(containerId: String) {
        client.post("/containers/$containerId/restart")
    }

    override suspend fun startContainer(containerId: String) {
        client.post("/containers/$containerId/start")
    }
}
