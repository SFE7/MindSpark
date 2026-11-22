package com.mindspark.compose.core.auth

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAtMillis: Long
)

class SessionExpired : Exception("Session expired")
