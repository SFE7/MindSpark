package com.mindspark.compose.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.connectivity.ConnectionBuilder
import com.mindspark.compose.core.config.EnvConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps AppAuth to launch the Keycloak browser login and exchange the
 * authorisation code for tokens.
 *
 * Service configuration is built directly from the issuer endpoints — no HTTP
 * discovery round-trip, so no HTTPS check is needed for the LAN `http://` URL.
 *
 * Call [dispose] when the owning screen is destroyed to avoid
 * ServiceConnectionLeaked (done in DisposableEffect in Task 5).
 */
class LoginLauncher(env: EnvConfig, context: Context) {

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("${env.issuer}/protocol/openid-connect/auth"),
        Uri.parse("${env.issuer}/protocol/openid-connect/token")
    )

    // Allow plain http:// for LAN development (AppAuth defaults to https-only)
    private val appAuthConfig = AppAuthConfiguration.Builder()
        .setConnectionBuilder(HttpConnectionBuilder())
        .build()

    private val authService = AuthorizationService(context, appAuthConfig)

    /** Returns an Intent that opens the Keycloak login page in a browser tab. */
    fun authIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            "mobile",
            ResponseTypeValues.CODE,
            Uri.parse("com.mindspark.compose://callback")
        )
            .setScopes("openid", "profile", "email")
            // prompt=login prevents a leftover browser session from silently
            // signing in the previous user (matches web Angular behaviour)
            .setPrompt(AuthorizationRequest.Prompt.LOGIN)
            .build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Processes the Activity result from the browser redirect.
     * No response AND no exception → user cancelled.
     */
    fun handleResult(
        data: Intent?,
        onTokens: (AuthTokens) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val resp = AuthorizationResponse.fromIntent(data ?: run { onCancelled(); return })
        val ex = AuthorizationException.fromIntent(data)
        if (resp == null) {
            if (ex != null) onError(ex.message ?: "Login failed")
            else onCancelled()
            return
        }
        authService.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
            when {
                tokenResp != null -> onTokens(
                    AuthTokens(
                        accessToken = tokenResp.accessToken ?: "",
                        refreshToken = tokenResp.refreshToken ?: "",
                        idToken = tokenResp.idToken ?: "",
                        expiresAtMillis = tokenResp.accessTokenExpirationTime ?: Long.MAX_VALUE
                    )
                )
                tokenEx != null -> onError(tokenEx.message ?: "Token exchange failed")
                else -> onCancelled()
            }
        }
    }

    fun dispose() {
        authService.dispose()
    }

    // ── inner class ──────────────────────────────────────────────────────────

    /** Allows AppAuth to open plain http:// connections on the LAN. */
    private class HttpConnectionBuilder : ConnectionBuilder {
        override fun openConnection(uri: Uri): HttpURLConnection {
            val url = URL(uri.toString())
            return (url.openConnection() as HttpURLConnection).also { conn ->
                conn.connectTimeout = 15_000
                conn.readTimeout = 10_000
            }
        }
    }
}
