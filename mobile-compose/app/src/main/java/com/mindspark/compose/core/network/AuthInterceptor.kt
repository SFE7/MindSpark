package com.mindspark.compose.core.network

import com.mindspark.compose.core.auth.AuthService
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val auth: AuthService) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = auth.validAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
