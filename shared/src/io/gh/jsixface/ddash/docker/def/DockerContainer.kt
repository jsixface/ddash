package io.gh.jsixface.ddash.docker.def

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerContainer(
    @SerialName("Id") val id: String,
    @SerialName("Names") val names: List<String> = emptyList(),
    @SerialName("Image") val image: String,
    @SerialName("State") val state: String,
    @SerialName("Status") val status: String,
    @SerialName("Ports") val ports: List<DockerPort> = emptyList(),
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
) {
    override fun toString(): String {
        return "Container($names: $image ($state/$status)"
    }
}

@Serializable
data class DockerPort(
    @SerialName("IP") val ip: String? = null,
    @SerialName("PrivatePort") val privatePort: Int,
    @SerialName("PublicPort") val publicPort: Int? = null,
    @SerialName("Type") val type: String,
) {
    override fun toString(): String {
        return "$ip:$privatePort -> $publicPort/$type"
    }
}
