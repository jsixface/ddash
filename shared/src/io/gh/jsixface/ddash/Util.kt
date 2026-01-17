package io.gh.jsixface.ddash

expect fun getEnv(key: EnvVars): String?

expect fun setupShutdownHook(block: () -> Unit)
