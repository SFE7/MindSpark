package com.mindspark.compose.core.auth

interface AuthGateway {
    /** Blocking call. Throws on network error or non-2xx. */
    fun refresh(refreshToken: String): AuthTokens
    /** Best-effort: does not throw. */
    fun logout(refreshToken: String)
}
