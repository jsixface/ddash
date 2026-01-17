package io.gh.jsixface.ddash.docker.def

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerEvent(
    @SerialName("Type") val type: String,
    @SerialName("Action") val action: String,
    @SerialName("Actor") val actor: DockerActor,
    @SerialName("time") val time: Long,
    @SerialName("timeNano") val timeNano: Long,
)

@Serializable
data class DockerActor(
    @SerialName("ID") val id: String,
    @SerialName("Attributes") val attributes: Map<String, String> = emptyMap(),
)
