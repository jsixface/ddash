package io.gh.jsixface.ddash.docker

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class UnixSocketDockerApiClientTest {

    private fun client(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        UnixSocketDockerApiClient(HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        })

    private fun frame(stream: Int, text: String): ByteArray {
        val payload = text.encodeToByteArray()
        val header = ByteArray(8)
        header[0] = stream.toByte()
        header[4] = (payload.size ushr 24).toByte()
        header[5] = (payload.size ushr 16).toByte()
        header[6] = (payload.size ushr 8).toByte()
        header[7] = payload.size.toByte()
        return header + payload
    }

    @Test
    fun `event stream completes when the daemon closes it`() = runTest {
        val body = """
            {"Type":"container","Action":"start","Actor":{"ID":"abc","Attributes":{}},"time":1,"timeNano":1}

            not json
            {"Type":"container","Action":"die","Actor":{"ID":"def"}}
        """.trimIndent() + "\n"
        val api = client { respond(ByteReadChannel(body.encodeToByteArray()), HttpStatusCode.OK) }

        // Collected on another dispatcher than the test's, the situation that used to trip the flow invariant check.
        val events = withContext(Dispatchers.Default) { api.events().toList() }

        assertEquals(listOf("start", "die"), events.map { it.action })
        assertEquals(listOf("abc", "def"), events.map { it.actor.id })
    }

    @Test
    fun `event stream failure propagates so the caller can reconnect`() = runTest {
        val api = client { respond("boom", HttpStatusCode.InternalServerError) }
        var failure: Throwable? = null
        api.events().catch { failure = it }.toList()
        assertTrue(failure != null)
    }

    @Test
    fun `multiplexed logs are decoded and stripped of ANSI codes and the flow completes`() = runTest {
        val bytes = frame(1, "\u001B[31mred\u001B[0m line\n") + frame(2, "err line\n")
        val api = client { respond(ByteReadChannel(bytes), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/vnd.docker.multiplexed-stream")) }

        val chunks = withContext(Dispatchers.Default) { api.containerLogs("c1", follow = false).toList() }

        assertEquals(listOf("red line\n", "err line\n"), chunks)
    }

    @Test
    fun `raw tty logs are decoded line by line`() = runTest {
        val api = client { respond(ByteReadChannel("one\ntwo\n".encodeToByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/vnd.docker.raw-stream")) }
        assertEquals(listOf("one\n", "two\n"), api.containerLogs("c1", follow = false).toList())
    }

    @Test
    fun `absurd log frame sizes end the stream instead of allocating`() = runTest {
        val header = byteArrayOf(1, 0, 0, 0, 0x7F, -1, -1, -1) // ~2 GiB claimed
        val api = client { respond(ByteReadChannel(header + "x".encodeToByteArray()), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/vnd.docker.multiplexed-stream")) }
        assertEquals(emptyList(), api.containerLogs("c1", follow = false).toList())
    }

    @Test
    fun `container actions map Docker statuses to results`() = runTest {
        val api = client { request ->
            when (request.url.encodedPath) {
                "/containers/ok/stop" -> respond("", HttpStatusCode.NoContent)
                "/containers/already/stop" -> respond("", HttpStatusCode.NotModified)
                "/containers/gone/stop" -> respond("""{"message":"No such container"}""", HttpStatusCode.NotFound)
                else -> respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError)
            }
        }
        api.stopContainer("ok")
        api.stopContainer("already") // 304: already stopped, not an error
        assertFailsWith<ContainerNotFoundException> { api.stopContainer("gone") }
        assertFailsWith<DockerApiException> { api.stopContainer("broken") }
    }

    @Test
    fun `list failures are wrapped in DockerApiException`() = runTest {
        val api = client { respond("nope", HttpStatusCode.InternalServerError) }
        assertFailsWith<DockerApiException> { api.listContainers() }
    }

    @Test
    fun `list parses containers`() = runTest {
        val api = client {
            respond(
                """[{"Id":"c1","Names":["/web"],"Image":"nginx","State":"running","Status":"Up","Labels":{"ddash.enable":"true"}}]""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertEquals("c1", api.listContainers().first().id)
    }
}
