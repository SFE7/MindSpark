package com.mindspark.compose.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.mindspark.compose.core.config.EnvConfig

/**
 * OkHttp-based implementation of [AuthGateway].
 *
 * Primary constructor is test-friendly (raw URLs). The secondary constructor
 * derives the URLs from [EnvConfig] and is used in production.
 */
class OkHttpAuthGateway(
    private val tokenUrl: String,
    private val logoutUrl: String,
    private val client: OkHttpClient
) : AuthGateway {

    constructor(env: EnvConfig, client: OkHttpClient) : this(
        tokenUrl = "${env.issuer}/protocol/openid-connect/token",
        logoutUrl = "${env.issuer}/protocol/openid-connect/logout",
        client = client
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("id_token") val idToken: String = "",
        @SerialName("expires_in") val expiresIn: Long = 300L
    )

    override fun refresh(refreshToken: String): AuthTokens {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", "mobile")
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder().url(tokenUrl).post(body).build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Token refresh failed: HTTP ${resp.code}")
            }
            val raw = resp.body?.string() ?: throw Exception("Empty token response")
            val parsed = json.decodeFromString<TokenResponse>(raw)
            return AuthTokens(
                accessToken = parsed.accessToken,
                refreshToken = parsed.refreshToken.ifEmpty { refreshToken },
                idToken = parsed.idToken,
                // expiresAtMillis derived from server's expires_in
                expiresAtMillis = System.currentTimeMillis() + parsed.expiresIn * 1_000L
            )
        }
    }

    override fun logout(refreshToken: String) {
        val body = FormBody.Builder()
            .add("client_id", "mobile")
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder().url(logoutUrl).post(body).build()
        try {
            client.newCall(request).execute().use { /* fire-and-forget */ }
        } catch (_: Exception) {
            // best-effort: ignore logout network errors
        }
    }
}
