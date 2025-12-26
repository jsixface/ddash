package io.gh.jsixface.ddash

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val dockerSocket: String = getEnv("DOCKER_SOCK") ?: "/var/run/docker.sock",
    val port: Int = getEnv("PORT")?.toIntOrNull() ?: 8080,
    val host: String = getEnv("LISTEN_ADDR") ?: "0.0.0.0"
)
