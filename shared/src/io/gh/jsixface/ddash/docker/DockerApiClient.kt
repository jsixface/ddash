package io.gh.jsixface.ddash.docker

import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerImage

interface DockerApiClient {
    suspend fun listImages(): List<DockerImage>

    suspend fun listContainers(): List<DockerContainer>
}
