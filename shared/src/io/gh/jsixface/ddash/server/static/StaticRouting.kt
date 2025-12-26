package io.gh.jsixface.ddash.server.static

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromFilePath
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class StaticConfig {
    var indexFile: String = "index.html"
    var excludeDotFiles: Boolean = true
    var rootPath: String = "."
}

fun Route.staticFiles(remotePath: String = "/", configure: StaticConfig.() -> Unit = {}) {
    val config = StaticConfig().apply(configure)
    val root = Path(config.rootPath)

    if (!SystemFileSystem.exists(root)) {
        println("Warning: Static root path does not exist: ${config.rootPath}")
        return
    }

    walkDirectory(root, root, remotePath, config)
}

private fun Route.walkDirectory(current: Path, root: Path, remotePath: String, config: StaticConfig) {
    val metadata = SystemFileSystem.metadataOrNull(current) ?: return
    
    if (metadata.isDirectory) {
        try {
            SystemFileSystem.list(current).forEach { child ->
                if (config.excludeDotFiles && child.name.startsWith(".")) {
                    return@forEach
                }
                walkDirectory(child, root, remotePath, config)
            }
        } catch (e: Exception) {
            // Handle or log error
        }
    } else {
        registerFileRoute(current, root, remotePath, config)
    }
}

private fun Route.registerFileRoute(file: Path, root: Path, remotePath: String, config: StaticConfig) {
    val relativePath = getRelativePath(file, root)
    val normalizedRemotePath = remotePath.removeSuffix("/")
    
    val routePath = if (relativePath == config.indexFile) {
        normalizedRemotePath.ifEmpty { "/" }
    } else {
        "$normalizedRemotePath/$relativePath"
    }
    
    get(routePath) {
        val bytes = try {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError)
            return@get
        }
        
        val contentType = ContentType.fromFilePath(file.name).firstOrNull() ?: ContentType.Application.OctetStream
        call.respondBytes(bytes, contentType)
    }
    
    // Also register the index file at the root of its directory if applicable
//    if (file.name == config.indexFile) {
//        val parent = file.parent
//        if (parent != null) {
//            val parentRelative = getRelativePath(parent, root)
//            val dirRoutePath = if (parentRelative.isEmpty()) {
//                if (normalizedRemotePath.isEmpty()) "/" else normalizedRemotePath
//            } else {
//                "$normalizedRemotePath/$parentRelative"
//            }
//
//            if (dirRoutePath != routePath) {
//                get(dirRoutePath) {
//                    val bytes = try {
//                        SystemFileSystem.source(file).buffered().use { it.readByteArray() }
//                    } catch (e: Exception) {
//                        call.respond(HttpStatusCode.InternalServerError)
//                        return@get
//                    }
//                    val contentType = ContentType.fromFilePath(file.name).firstOrNull() ?: ContentType.Application.OctetStream
//                    call.respondBytes(bytes, contentType)
//                }
//            }
//        }
//    }
}

private fun getRelativePath(path: Path, root: Path): String {
    val rootStr = root.toString().removeSuffix("/")
    val pathStr = path.toString()
    return pathStr.removePrefix(rootStr).removePrefix("/")
}
