package io.gh.jsixface.ddash

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

expect fun getEnv(key: EnvVars): String?

fun String.removeAnsiCodes(): String {
    val ansiRegex = Regex("\u001B\\[[;\\d]*[A-Za-z]")
    return this.replace(ansiRegex, "")
}

expect fun setupShutdownHook(block: () -> Unit)

/**
 * HTTP client for talking to remote HTTPS servers. The CIO engine has no TLS support on Kotlin/Native, so native
 * targets use the Curl engine instead.
 */
expect fun tlsHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
