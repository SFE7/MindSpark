package com.mindspark.compose.core.network

import com.mindspark.compose.core.auth.AuthService
import com.mindspark.compose.core.auth.SessionExpired
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(private val auth: AuthService) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Retry only once per request
        if (response.priorResponse != null) {
            return null
        }
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        return try {
            val newToken = auth.refreshAccessToken(failedToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        } catch (e: SessionExpired) {
            null
        }
    }
}
