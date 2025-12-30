package io.gh.jsixface.ddash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: EnvVars): String? {
    return getenv(key.name)?.toKString()
}
