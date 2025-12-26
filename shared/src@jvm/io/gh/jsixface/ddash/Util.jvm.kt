package io.gh.jsixface.ddash


actual fun getEnv(key: String): String? {
    return System.getenv(key)
}
