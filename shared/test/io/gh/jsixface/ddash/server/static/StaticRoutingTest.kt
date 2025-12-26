package io.gh.jsixface.ddash.server.static

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

class StaticRoutingTest {

    private val testRootDir = "test-static-root"

    @BeforeTest
    fun setup() {
        val root = Path(testRootDir)
        if (!SystemFileSystem.exists(root)) {
            SystemFileSystem.createDirectories(root)
        }
        val indexFile = Path(testRootDir, "index.html")
        SystemFileSystem.sink(indexFile).buffered().use { it.writeString("Index Content") }
        
        val subDir = Path(testRootDir, "sub")
        SystemFileSystem.createDirectories(subDir)
        val subIndexFile = Path(subDir, "index.html")
        SystemFileSystem.sink(subIndexFile).buffered().use { it.writeString("Sub Index Content") }
    }

    @AfterTest
    fun teardown() {
        // Cleanup would be nice but SystemFileSystem doesn't have a simple recursive delete in this version maybe?
        // For now let's just leave it or try to delete what we know
    }

    @Test
    fun testIndexFileAtRoot() = testApplication {
        routing {
            staticFiles("/") {
                rootPath = testRootDir
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Index Content", response.bodyAsText())
        
        val responseExplicit = client.get("/index.html")
        assertEquals(HttpStatusCode.OK, responseExplicit.status)
        assertEquals("Index Content", responseExplicit.bodyAsText())
    }

    @Test
    fun testIndexFileInSubDir() = testApplication {
        routing {
            staticFiles("/") {
                rootPath = testRootDir
            }
        }

        val response = client.get("/sub")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Sub Index Content", response.bodyAsText())

        val responseExplicit = client.get("/sub/index.html")
        assertEquals(HttpStatusCode.OK, responseExplicit.status)
        assertEquals("Sub Index Content", responseExplicit.bodyAsText())
    }
}
