package io.gh.jsixface.ddash.docker

import io.gh.jsixface.ddash.docker.def.DockerContainer
import io.gh.jsixface.ddash.docker.def.DockerImage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class UnixSocketDockerApiClient(private val client: HttpClient) : DockerApiClient {
    override suspend fun listImages(): List<DockerImage> = client.get("/images/json").body()

    override suspend fun listContainers(): List<DockerContainer> = client.get("/containers/json").body()
}
