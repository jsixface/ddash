package io.gh.jsixface.ddash

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val dockerSocket: String = "/var/run/docker.sock"
)
