package io.gh.jsixface.ddash.docker.def

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerImage(
    @SerialName("Id") val id: String,
    @SerialName("RepoTags") val repoTags: List<String> = emptyList(),
    @SerialName("Size") val size: Long,
    @SerialName("Created") val created: Long,
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
) {
}
