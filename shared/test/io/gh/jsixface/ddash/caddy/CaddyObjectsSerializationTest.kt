package io.gh.jsixface.ddash.caddy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CaddyObjectsSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun testReverseProxySerialization() {
        val handler: CaddyHandler = CaddyHandler.ReverseProxy(
            upstreams = listOf(CaddyUpstream(dial = "localhost:8080"))
        )
        val jsonString = json.encodeToString(CaddyHandler.serializer(), handler)
        assertEquals("""{"handler":"reverse_proxy","upstreams":[{"dial":"localhost:8080"}]}""", jsonString)

        val decoded = json.decodeFromString(CaddyHandler.serializer(), jsonString)
        assertIs<CaddyHandler.ReverseProxy>(decoded)
        assertEquals("localhost:8080", decoded.upstreams[0].dial)
    }

    @Test
    fun testEncodeSerialization() {
        val handler: CaddyHandler = CaddyHandler.Encode(
            encodings = buildJsonObject {
                put("gzip", buildJsonObject { })
            }, prefer = listOf("gzip")
        )
        val jsonString = json.encodeToString(CaddyHandler.serializer(), handler)
        assertEquals("""{"handler":"encode","encodings":{"gzip":{}},"prefer":["gzip"]}""", jsonString)

        val decoded = json.decodeFromString(CaddyHandler.serializer(), jsonString)
        assertIs<CaddyHandler.Encode>(decoded)
        assertTrue(decoded.encodings.containsKey("gzip"))
    }

    @Test
    fun testHeadersSerialization() {
        val handler: CaddyHandler = CaddyHandler.Headers(
            response = buildJsonObject {
                put("set", buildJsonObject {
                    put("Content-Type", "text/plain")
                })
            })
        val jsonString = json.encodeToString(CaddyHandler.serializer(), handler)
        assertEquals("""{"handler":"headers","response":{"set":{"Content-Type":"text/plain"}}}""", jsonString)

        val decoded = json.decodeFromString(CaddyHandler.serializer(), jsonString)
        assertIs<CaddyHandler.Headers>(decoded)
    }

    @Test
    fun testVarsSerialization() {
        val handler: CaddyHandler = CaddyHandler.Vars(root = "/var/www")
        val jsonString = json.encodeToString(CaddyHandler.serializer(), handler)
        assertEquals("""{"handler":"vars","root":"/var/www"}""", jsonString)

        val decoded = json.decodeFromString(CaddyHandler.serializer(), jsonString)
        assertIs<CaddyHandler.Vars>(decoded)
        assertEquals("/var/www", decoded.root)
    }

    @Test
    fun testSubrouteSerialization() {
        val handler: CaddyHandler = CaddyHandler.Subroute(
            routes = listOf(
                CaddyRoute(
                    handle = listOf(CaddyHandler.Vars(root = "/sub"))
                )
            )
        )
        val jsonString = json.encodeToString(CaddyHandler.serializer(), handler)
        assertEquals("""{"handler":"subroute","routes":[{"handle":[{"handler":"vars","root":"/sub"}]}]}""", jsonString)

        val decoded = json.decodeFromString(CaddyHandler.serializer(), jsonString)
        assertIs<CaddyHandler.Subroute>(decoded)
    }

    @Test
    fun testCaddyServersSerialization() {
        val caddyServers = CaddyServers(
            servers = mapOf(
                "srv0" to CaddyServer(
                    listen = listOf(":80"), routes = listOf(
                        CaddyRoute(
                            match = listOf(CaddyMatcher(host = listOf("example.com"))),
                            handle = listOf(CaddyHandler.ReverseProxy(listOf(CaddyUpstream("localhost:8080"))))
                        )
                    )
                )
            )
        )
        val jsonString = json.encodeToString(CaddyServers.serializer(), caddyServers)
        val decoded = json.decodeFromString(CaddyServers.serializer(), jsonString)
        assertEquals(caddyServers, decoded)
    }
}
