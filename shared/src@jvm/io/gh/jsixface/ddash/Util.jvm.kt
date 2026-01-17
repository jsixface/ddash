package io.gh.jsixface.ddash

actual fun setupShutdownHook(block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread {
        block()
    })
}

actual fun getEnv(key: EnvVars): String? {
    return System.getenv(key.name)
}
