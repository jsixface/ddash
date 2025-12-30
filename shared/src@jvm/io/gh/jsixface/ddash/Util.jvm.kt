package io.gh.jsixface.ddash


actual fun getEnv(key: EnvVars): String? {
    return System.getenv(key.name)
}
