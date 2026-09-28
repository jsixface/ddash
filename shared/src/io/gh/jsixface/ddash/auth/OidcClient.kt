package io.gh.jsixface.ddash.auth

import co.touchlab.kermit.Logger
import io.gh.jsixface.ddash.OidcSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class OidcDiscovery(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("userinfo_endpoint") val userinfoEndpoint: String? = null,
)

@Serializable
data class OidcTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
)

/** The authenticated user, as described by the identity provider. */
@Serializable
data class AuthUser(
    val subject: String,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
) {
    val displayName: String get() = name ?: username ?: email ?: subject
}

class OidcException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal OpenID Connect relying party implementing the authorization-code flow with PKCE.
 *
 * The ID token is obtained directly from the token endpoint over TLS, so (per OIDC Core §3.1.3.7) its signature does not
 * need to be verified. User claims are read from the userinfo endpoint when the provider offers one, otherwise from the
 * ID token payload.
 */
class OidcClient(
    private val settings: OidcSettings,
    private val http: HttpClient,
) {
    private val logger = Logger.withTag("OidcClient")
    private val json = Json { ignoreUnknownKeys = true }
    private val discoveryLock = Mutex()
    private var cachedDiscovery: OidcDiscovery? = null

    /** Discovery document, fetched lazily (so an unavailable IdP never blocks startup) and cached on success. */
    suspend fun discovery(): OidcDiscovery = discoveryLock.withLock {
        cachedDiscovery ?: fetchDiscovery().also { cachedDiscovery = it }
    }

    private suspend fun fetchDiscovery(): OidcDiscovery {
        val url = "${settings.issuerUrl}/.well-known/openid-configuration"
        logger.i { "Fetching OIDC discovery document from $url" }
        val doc = try {
            http.get(url).body<OidcDiscovery>()
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            throw OidcException("Could not fetch OIDC discovery document from $url", e)
        }
        if (doc.issuer.trimEnd('/') != settings.issuerUrl) {
            throw OidcException("OIDC issuer mismatch: configured '${settings.issuerUrl}' but provider reports '${doc.issuer}'")
        }
        return doc
    }

    suspend fun authorizationUrl(state: String, codeVerifier: String): String {
        val builder = URLBuilder(discovery().authorizationEndpoint)
        builder.parameters.apply {
            append("response_type", "code")
            append("client_id", settings.clientId)
            append("redirect_uri", settings.redirectUri)
            append("scope", settings.scopes)
            append("state", state)
            append("code_challenge", Crypto.pkceChallenge(codeVerifier))
            append("code_challenge_method", "S256")
        }
        return builder.buildString()
    }

    suspend fun exchangeCode(code: String, codeVerifier: String): OidcTokenResponse {
        val endpoint = discovery().tokenEndpoint
        return try {
            http.submitForm(
                url = endpoint,
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", settings.redirectUri)
                    append("code_verifier", codeVerifier)
                    if (settings.clientSecret == null) append("client_id", settings.clientId)
                },
            ) {
                settings.clientSecret?.let { basicAuth(settings.clientId, it) }
            }.body()
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            throw OidcException("Token exchange failed", e)
        }
    }

    suspend fun fetchUser(tokens: OidcTokenResponse): AuthUser {
        val userinfo = discovery().userinfoEndpoint
        val claims: JsonObject = when {
            userinfo != null && tokens.accessToken != null -> try {
                http.get(userinfo) { header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}") }.body()
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                throw OidcException("Could not fetch user info", e)
            }

            tokens.idToken != null -> idTokenClaims(tokens.idToken)
            else -> throw OidcException("Provider returned neither a usable access token nor an ID token")
        }
        val subject = claims.string("sub") ?: throw OidcException("User info has no 'sub' claim")
        return AuthUser(
            subject = subject,
            username = claims.string("preferred_username"),
            name = claims.string("name"),
            email = claims.string("email"),
        )
    }

    private fun idTokenClaims(idToken: String): JsonObject {
        val parts = idToken.split('.')
        if (parts.size < 2) throw OidcException("Malformed ID token")
        return try {
            json.parseToJsonElement(Crypto.base64UrlDecode(parts[1]).decodeToString()).jsonObject
        } catch (e: Exception) {
            throw OidcException("Malformed ID token payload", e)
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

    /** Whether the redirect URI is served over https (cookie `Secure` attribute). */
    val redirectIsSecure: Boolean get() = Url(settings.redirectUri).protocol.name == "https"
}
