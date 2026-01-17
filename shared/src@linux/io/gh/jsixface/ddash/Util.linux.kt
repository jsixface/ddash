package io.gh.jsixface.ddash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import platform.posix.SIGINT
import platform.posix.getenv
import platform.posix.signal


private var shutdownBlock: (() -> Unit)? = null

@OptIn(ExperimentalForeignApi::class)
actual fun setupShutdownHook(block: () -> Unit) {
    shutdownBlock = block
    signal(SIGINT, staticCFunction { _: Int ->
        val blockToCall = shutdownBlock
        if (blockToCall != null) {
            blockToCall()
        }
    })
}

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: EnvVars): String? {
    return getenv(key.name)?.toKString()
}
