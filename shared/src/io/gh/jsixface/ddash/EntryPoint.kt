package io.gh.jsixface.ddash

import io.gh.jsixface.ddash.docker.UnixSocketDockerApiClient
import kotlinx.coroutines.runBlocking

fun runApplication(args: Array<String>) = runBlocking {
    println("Starting Docker Dashboard application...")
    val settings = Settings()
    val dockerClient = DockerClient.get(settings)
    val apiClient = UnixSocketDockerApiClient(dockerClient)
    val containers = apiClient.listContainers()

    containers.forEach { println(it) }
}
