package io.gh.jsixface.ddash

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

actual fun setupShutdownHook(block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread {
        block()
    })
}

actual fun getEnv(key: EnvVars): String? {
    return System.getenv(key.name)
}

actual fun tlsHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO, config)
