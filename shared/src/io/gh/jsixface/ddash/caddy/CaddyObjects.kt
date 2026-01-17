package io.gh.jsixface.ddash.caddy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

@Serializable
data class CaddyServer(
    val listen: List<String>,
    val routes: List<CaddyRoute>,
)

@Serializable
data class CaddyServers(
    val servers: Map<String, CaddyServer>,
)

@Serializable
data class CaddyRoute(
    val match: List<CaddyMatcher>? = null,
    val terminal: Boolean? = null,
    val handle: List<CaddyHandler>,
)

@Serializable
data class CaddyMatcher(
    val host: List<String>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("handler")
sealed class CaddyHandler {
    @Serializable
    @SerialName("reverse_proxy")
    data class ReverseProxy(val upstreams: List<CaddyUpstream>) : CaddyHandler()

    @Serializable
    @SerialName("file_server")
    data class FileServer(val root: String? = null) : CaddyHandler() {
    }

    @Serializable
    @SerialName("encode")
    data class Encode(
        val encodings: JsonObject,
        val prefer: List<String>,
    ) : CaddyHandler()

    @Serializable
    @SerialName("headers")
    data class Headers(val response: JsonObject) : CaddyHandler()

    @Serializable
    @SerialName("static_response")
    data class StaticResponse(val body: String) : CaddyHandler()


    @Serializable
    @SerialName("vars")
    data class Vars(val root: String) : CaddyHandler()

    @Serializable
    @SerialName("subroute")
    data class Subroute(val routes: List<CaddyRoute>) : CaddyHandler()
}

@Serializable
data class CaddyUpstream(
    val dial: String,
)
