package io.gh.jsixface.ddash.docker

import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerEvent
import io.gh.jsixface.ddash.docker.def.DockerImage
import kotlinx.coroutines.flow.Flow

interface DockerApiClient {
    suspend fun listImages(): List<DockerImage>

    suspend fun listContainers(): List<DockerContainer>

    suspend fun ping(): Boolean

    fun events(): Flow<DockerEvent>
}
