package io.gh.jsixface.ddash.docker

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.gh.jsixface.ddash.removeAnsiCodes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLine
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

class UnixSocketDockerApiClient(private val client: HttpClient) : DockerApiClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = Logger.withTag("UnixSocketDockerApiClient")

    override suspend fun listImages(): List<DockerImage> = dockerCall("list images") {
        client.get("/images/json").body()
    }

    override suspend fun listContainers(): List<DockerContainer> = dockerCall("list containers") {
        client.get("/containers/json") {
            url {
                parameters.append("all", "true")
            }
        }.body()
    }

    /** Wraps any transport or HTTP failure in [DockerApiException] so callers deal with a single exception type. */
    private suspend fun <T> dockerCall(what: String, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw DockerApiException("Docker request to $what failed", e)
    }

    override suspend fun ping(): Boolean {
        return try {
            client.get("/_ping").status.value == 200
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.d(e) { "Docker ping failed" }
            false
        }
    }

    // `channelFlow` (rather than `flow`) lets us send from inside the HTTP client's `execute { }` block, which runs in
    // a different coroutine context (that would violate the `flow` context-preservation invariant). Unlike
    // `callbackFlow` it needs no `awaitClose`, so the flow completes as soon as the daemon closes the stream.
    override fun events(): Flow<DockerEvent> = channelFlow {
        client.prepareGet("/events") {
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
            url {
                parameters.append("filters", """{"type":["container"],"event":[${
                    CONTAINER_EVENT_ACTIONS.joinToString(",") { "\"$it\"" }
                }]}""")
            }
        }.execute { response ->
            val channel: ByteReadChannel = response.body()
            while (!channel.isClosedForRead && isActive) {
                val line = channel.readLine() ?: break
                if (line.isNotEmpty()) {
                    try {
                        send(json.decodeFromString<DockerEvent>(line))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.e { "Error decoding Docker event: $line. Reason: ${e.message}" }
                    }
                }
            }
        }
        logger.i { "Docker event stream ended" }
    }

    override fun containerLogs(
        containerId: String,
        tail: Int,
        follow: Boolean,
        timestamps: Boolean,
    ): Flow<String> = channelFlow {
        client.prepareGet("/containers/$containerId/logs") {
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
            val isRawStream = response.headers["Content-Type"] == "application/vnd.docker.raw-stream"
            val channel: ByteReadChannel = response.body()
            if (isRawStream) {
                while (!channel.isClosedForRead && isActive) {
                    val line = channel.readLine() ?: break
                    send((line + "\n").removeAnsiCodes())
                }
            } else {
                readMultiplexedLogs(channel)
            }
        }
    }

    private suspend fun ProducerScope<String>.readMultiplexedLogs(channel: ByteReadChannel) {
        while (!channel.isClosedForRead && isActive) {
            val header = ByteArray(8)
            try {
                channel.readFully(header)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // End of stream (or the connection dropped) between frames: nothing more to read.
                break
            }
            val size = ((header[4].toInt() and 0xFF) shl 24) or
                ((header[5].toInt() and 0xFF) shl 16) or
                ((header[6].toInt() and 0xFF) shl 8) or
                (header[7].toInt() and 0xFF)

            if (size < 0 || size > MAX_LOG_FRAME_BYTES) {
                logger.w { "Unexpected log frame size: $size. Something is wrong with the stream." }
                break
            }
            if (size == 0) continue
            try {
                val payload = ByteArray(size)
                channel.readFully(payload)
                send(payload.decodeToString().removeAnsiCodes())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.e(e) { "Error reading log payload" }
                break
            }
        }
    }

    override suspend fun stopContainer(containerId: String) = containerAction(containerId, "stop")

    override suspend fun restartContainer(containerId: String) = containerAction(containerId, "restart")

    override suspend fun startContainer(containerId: String) = containerAction(containerId, "start")

    private suspend fun containerAction(containerId: String, action: String) {
        val response = dockerCall("$action container $containerId") {
            client.post("/containers/$containerId/$action") { expectSuccess = false }
        }
        when {
            response.status.isSuccess() -> Unit
            // Docker answers 304 when the container is already in the requested state.
            response.status == HttpStatusCode.NotModified -> logger.d { "Container $containerId: $action was a no-op" }
            response.status == HttpStatusCode.NotFound -> throw ContainerNotFoundException(containerId)
            else -> throw DockerApiException("Docker refused to $action container $containerId: ${response.status}")
        }
    }

    companion object {
        /** Container events DDash reacts to. */
        val CONTAINER_EVENT_ACTIONS = listOf("start", "stop", "die", "destroy", "rename", "update")

        /** Docker log frames are at most a few tens of KiB; anything near this is a corrupt stream. */
        private const val MAX_LOG_FRAME_BYTES = 4 * 1024 * 1024
    }
}
