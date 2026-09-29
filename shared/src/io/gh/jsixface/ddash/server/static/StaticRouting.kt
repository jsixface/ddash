package io.gh.jsixface.ddash.server.static

import co.touchlab.kermit.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromFilePath
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class StaticConfig {
    var indexFile: String = "index.html"
    var excludeDotFiles: Boolean = true
    var rootPath: String = "."
}

private val logger = Logger.withTag("StaticRouting")

fun Route.staticFiles(remotePath: String = "/", configure: StaticConfig.() -> Unit = {}) {
    val config = StaticConfig().apply(configure)
    val root = Path(config.rootPath)

    if (!SystemFileSystem.exists(root)) {
        logger.w { "Static root path does not exist: ${config.rootPath}" }
        return
    }

    walkDirectory(root, relative = "", remotePath, config)
}

/**
 * Registers a route for every file below [current]. [relative] is the `/`-separated path from the static root, built
 * from directory entry names rather than by string-stripping the root, so it is independent of how the platform
 * renders paths (absolute vs relative, `\` vs `/`).
 */
private fun Route.walkDirectory(current: Path, relative: String, remotePath: String, config: StaticConfig) {
    val metadata = SystemFileSystem.metadataOrNull(current) ?: return

    if (metadata.isDirectory) {
        try {
            SystemFileSystem.list(current).forEach { child ->
                if (config.excludeDotFiles && child.name.startsWith(".")) {
                    return@forEach
                }
                val childRelative = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                walkDirectory(child, childRelative, remotePath, config)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Could not list static directory $current" }
        }
    } else {
        registerFileRoute(current, relative, remotePath, config)
    }
}

private fun Route.registerFileRoute(file: Path, relativePath: String, remotePath: String, config: StaticConfig) {
    val base = remotePath.removeSuffix("/")

    // An index file is served both under its own name and as its directory (`/`, `/sub`, `/sub/`).
    val routePaths = when {
        relativePath == config.indexFile -> listOf(base.ifEmpty { "/" }, "$base/$relativePath")
        relativePath.endsWith("/${config.indexFile}") -> {
            val dir = relativePath.removeSuffix("/${config.indexFile}")
            listOf("$base/$dir", "$base/$dir/", "$base/$relativePath")
        }

        else -> listOf("$base/$relativePath")
    }.distinct()

    val contentType = ContentType.fromFilePath(file.name).firstOrNull() ?: ContentType.Application.OctetStream

    routePaths.forEach { routePath ->
        logger.d { "Serving static file $file at $routePath" }
        get(routePath) {
            val bytes = try {
                SystemFileSystem.source(file).buffered().use { it.readByteArray() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.e(e) { "Could not read static file $file" }
                call.respond(HttpStatusCode.InternalServerError)
                return@get
            }
            call.respondBytes(bytes, contentType)
        }
    }
}
