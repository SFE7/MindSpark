package com.mindspark.compose.core.auth

interface TokenStore {
    fun read(): AuthTokens?
    fun save(t: AuthTokens)
    fun clear()
}
