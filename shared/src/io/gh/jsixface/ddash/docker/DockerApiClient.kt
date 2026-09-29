package io.gh.jsixface.ddash.docker

import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import kotlinx.coroutines.flow.Flow

/** The Docker daemon rejected a request (other than "not found"). */
class DockerApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The requested container does not exist (or is not one DDash manages). */
class ContainerNotFoundException(val containerId: String) : Exception("Container not found: $containerId")

interface DockerApiClient {
    suspend fun listImages(): List<DockerImage>

    suspend fun listContainers(): List<DockerContainer>

    suspend fun ping(): Boolean

    /**
     * Stream of Docker events. The flow completes when the daemon closes the stream and fails if the connection
     * breaks; callers are expected to reconnect.
     */
    fun events(): Flow<DockerEvent>

    /** Stream of log chunks. Completes when the log stream ends (e.g. the container stops when following). */
    fun containerLogs(
        containerId: String,
        tail: Int = 100,
        follow: Boolean = true,
        timestamps: Boolean = false,
    ): Flow<String>

    /** @throws ContainerNotFoundException when the container does not exist. */
    suspend fun stopContainer(containerId: String)

    /** @throws ContainerNotFoundException when the container does not exist. */
    suspend fun restartContainer(containerId: String)

    /** @throws ContainerNotFoundException when the container does not exist. */
    suspend fun startContainer(containerId: String)
}
