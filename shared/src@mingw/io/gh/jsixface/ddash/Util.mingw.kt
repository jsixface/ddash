package io.gh.jsixface.ddash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: String): String? {
    return getenv(key)?.toKString()
}
