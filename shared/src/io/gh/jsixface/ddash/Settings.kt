package io.gh.jsixface.ddash

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val dockerSocket: String = getEnv(EnvVars.DOCKER_SOCK) ?: "/var/run/docker.sock",
    val port: Int = getEnv(EnvVars.PORT).toIntOrWarn(EnvVars.PORT, 8080),
    val host: String = getEnv(EnvVars.LISTEN_ADDR) ?: "0.0.0.0",
    val caddyAdminUrl: String = getEnv(EnvVars.CADDY_ADMIN_URL) ?: "http://localhost:2019",
    val caddyAutoSaveConfig: Boolean = getEnv(EnvVars.CADDY_AUTO_SAVE_CONFIG).toBooleanOrWarn(EnvVars.CADDY_AUTO_SAVE_CONFIG),
    val caddySecureRouting: Boolean = getEnv(EnvVars.CADDY_SECURE_ROUTING).toBooleanOrWarn(EnvVars.CADDY_SECURE_ROUTING),
    val externalConfigPath: String = getEnv(EnvVars.EXTERNAL_CONFIG_PATH) ?: "/config/services.toml",
    val oidc: OidcSettings? = OidcSettings.fromEnv(),
)

/**
 * OpenID Connect settings. Present only when OIDC login is fully configured; otherwise the dashboard runs without
 * authentication (every endpoint is open), as it did before OIDC support was added.
 */
@Serializable
data class OidcSettings(
    val issuerUrl: String,
    val clientId: String,
    val clientSecret: String?,
    val redirectUri: String,
    val scopes: String = "openid profile email",
    /** Optional allow-list of e-mails / usernames / subjects. Empty means every authenticated user is allowed. */
    val allowedUsers: Set<String> = emptySet(),
    val sessionTtlHours: Int = 24,
) {
    companion object {
        fun fromEnv(): OidcSettings? {
            val issuer = getEnv(EnvVars.OIDC_ISSUER_URL)?.takeIf { it.isNotBlank() }
            val clientId = getEnv(EnvVars.OIDC_CLIENT_ID)?.takeIf { it.isNotBlank() }
            val redirectUri = getEnv(EnvVars.OIDC_REDIRECT_URI)?.takeIf { it.isNotBlank() }
            if (issuer == null && clientId == null && redirectUri == null) return null
            if (issuer == null || clientId == null || redirectUri == null) {
                Logger.withTag("Settings").w {
                    "OIDC is partially configured: OIDC_ISSUER_URL, OIDC_CLIENT_ID and OIDC_REDIRECT_URI are all " +
                        "required. OIDC login is disabled."
                }
                return null
            }
            return OidcSettings(
                issuerUrl = issuer.trim().trimEnd('/'),
                clientId = clientId.trim(),
                clientSecret = getEnv(EnvVars.OIDC_CLIENT_SECRET)?.takeIf { it.isNotBlank() },
                redirectUri = redirectUri.trim(),
                scopes = getEnv(EnvVars.OIDC_SCOPES)?.takeIf { it.isNotBlank() } ?: "openid profile email",
                allowedUsers = getEnv(EnvVars.OIDC_ALLOWED_USERS).orEmpty()
                    .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
                sessionTtlHours = getEnv(EnvVars.OIDC_SESSION_TTL_HOURS).toIntOrWarn(EnvVars.OIDC_SESSION_TTL_HOURS, 24)
                    .coerceAtLeast(1),
            )
        }
    }
}

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
    CADDY_AUTO_SAVE_CONFIG,
    EXTERNAL_CONFIG_PATH,
    OIDC_ISSUER_URL,
    OIDC_CLIENT_ID,
    OIDC_CLIENT_SECRET,
    OIDC_REDIRECT_URI,
    OIDC_SCOPES,
    OIDC_ALLOWED_USERS,
    OIDC_SESSION_TTL_HOURS,
}

private fun String?.toIntOrWarn(key: EnvVars, default: Int): Int {
    if (this == null) return default
    return toIntOrNull() ?: default.also {
        Logger.withTag("Settings").w { "Invalid integer '$this' for $key; using default $default." }
    }
}

private fun String?.toBooleanOrWarn(key: EnvVars): Boolean {
    if (this == null) return false
    return when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> false.also {
            Logger.withTag("Settings").w { "Invalid boolean '$this' for $key (expected true/false); using false." }
        }
    }
}
