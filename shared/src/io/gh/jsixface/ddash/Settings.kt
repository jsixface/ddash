package io.gh.jsixface.ddash

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val dockerSocket: String = getEnv(EnvVars.DOCKER_SOCK) ?: "/var/run/docker.sock",
    val port: Int = getEnv(EnvVars.PORT)?.toIntOrNull() ?: 8080,
    val host: String = getEnv(EnvVars.LISTEN_ADDR) ?: "0.0.0.0",
    val caddyAdminUrl: String = getEnv(EnvVars.CADDY_ADMIN_URL) ?: "http://localhost:2019",
    val caddySecureRouting: Boolean = getEnv(EnvVars.CADDY_SECURE_ROUTING)?.toBoolean() ?: false,
)

object Globals {
    val settings: Settings by lazy { initSettings() }

    private fun initSettings() = Settings() // TODO: initialize settings from config file
}

enum class EnvVars {
    DOCKER_SOCK,
    PORT,
    LISTEN_ADDR,
    CADDY_ADMIN_URL,
    CADDY_SECURE_ROUTING,
}
