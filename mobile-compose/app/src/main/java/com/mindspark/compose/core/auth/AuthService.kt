package com.mindspark.compose.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the authenticated session: proactive token refresh, single-flight
 * refresh on 401, and session-expired signalling.
 *
 * [clock] is injectable for testing (default: [System.currentTimeMillis]).
 * Keep this class free of coroutines: [OkHttpClient]'s Authenticator calls
 * [refreshAccessToken] from a background thread.
 */
class AuthService(
    private val gateway: AuthGateway,
    private val store: TokenStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    fun saveLogin(t: AuthTokens) {
        store.save(t)
        _sessionExpired.value = false
    }

    /**
     * Returns the current access token. Proactively refreshes if it expires
     * in under 30 seconds. Returns null if no session is stored or if
     * refresh fails (sessionExpired is set to true in that case).
     */
    fun validAccessToken(): String? {
        val tokens = store.read() ?: return null
        val remainingMs = tokens.expiresAtMillis - clock()
        return if (remainingMs < 30_000L) {
            try {
                refreshAccessToken(tokens.accessToken)
            } catch (e: SessionExpired) {
                null
            }
        } else {
            tokens.accessToken
        }
    }

    /**
     * Single-flight refresh. If the stored access token already differs from
     * [failedToken], another thread already refreshed — return the new token
     * without hitting the gateway again.
     *
     * On any gateway error: clears the store, sets [sessionExpired], throws [SessionExpired].
     */
    @Synchronized
    fun refreshAccessToken(failedToken: String?): String {
        val current = store.read() ?: run {
            _sessionExpired.value = true
            throw SessionExpired()
        }
        // Another thread already refreshed successfully
        if (current.accessToken != failedToken) return current.accessToken

        return try {
            val newTokens = gateway.refresh(current.refreshToken)
            store.save(newTokens)
            newTokens.accessToken
        } catch (e: Exception) {
            store.clear()
            _sessionExpired.value = true
            throw SessionExpired()
        }
    }

    fun logout() {
        val tokens = store.read()
        store.clear()
        _sessionExpired.value = false
        if (tokens != null) {
            gateway.logout(tokens.refreshToken) // best-effort; OkHttpAuthGateway ignores errors
        }
    }
}
