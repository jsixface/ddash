package io.gh.jsixface.ddash.caddy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

@Serializable
data class CaddyServer(
    val listen: List<String> = emptyList(),
    val routes: List<CaddyRoute> = emptyList(),
)

/**
 * Where a route for [host] lives. [hosts] lists every host matched by that same Caddy route, so callers can tell
 * whether deleting the route would also take out other hosts.
 */
data class RoutePlacement(
    val host: String,
    val serverId: String,
    val index: Int,
    val hosts: List<String> = listOf(host),
    /** The route's `@id`, if it has one (routes created by DDash do). */
    val id: String? = null,
    /**
     * The upstream `dial` address when the route is exactly one plain `reverse_proxy` handler with a single upstream
     * (the shape DDash creates); null for any other kind of route.
     */
    val upstream: String? = null,
) {
    /** Whether DDash may rewrite this route: a single-host route of the plain shape it creates itself. */
    val replaceable: Boolean get() = hosts.size <= 1 && upstream != null
}

@Serializable
data class CaddyServers(
    val servers: Map<String, CaddyServer>,
)

@Serializable
data class CaddyRoute(
    /** Caddy's config-wide unique identifier; DDash tags the routes it creates so they can be recognised. */
    @SerialName("@id") val id: String? = null,
    val match: List<CaddyMatcher>? = null,
    val terminal: Boolean? = null,
    @Serializable(with = LenientHandlerListSerializer::class)
    val handle: List<CaddyHandler> = emptyList(),
)

@Serializable
data class CaddyMatcher(
    // Matchers that don't use `host` (path, header, ...) simply have no hosts.
    val host: List<String> = emptyList(),
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
    data class FileServer(val root: String? = null) : CaddyHandler()

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
    data class StaticResponse(val body: String? = null) : CaddyHandler()


    @Serializable
    @SerialName("vars")
    data class Vars(val root: String) : CaddyHandler()

    @Serializable
    @SerialName("subroute")
    data class Subroute(val routes: List<CaddyRoute> = emptyList()) : CaddyHandler()

    /** Any handler type DDash doesn't model (rewrite, authentication, ...). Read-only: never sent back to Caddy. */
    @Serializable
    @SerialName("unknown")
    data class Unknown(val raw: JsonObject) : CaddyHandler()
}

/**
 * Decodes a route's `handle` array, mapping handler types DDash doesn't model to [CaddyHandler.Unknown] instead of
 * failing. `ignoreUnknownKeys` does not cover unknown polymorphic discriminators, so without this a single route
 * using e.g. `rewrite` would make the whole Caddy config unreadable.
 */
object LenientHandlerListSerializer : KSerializer<List<CaddyHandler>> {
    private val delegate = ListSerializer(CaddyHandler.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<CaddyHandler>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<CaddyHandler> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return jsonDecoder.decodeJsonElement().jsonArray.map { element ->
            try {
                jsonDecoder.json.decodeFromJsonElement(CaddyHandler.serializer(), element)
            } catch (_: SerializationException) {
                CaddyHandler.Unknown(element as? JsonObject ?: JsonObject(emptyMap()))
            }
        }
    }
}

@Serializable
data class CaddyUpstream(
    val dial: String,
)
