package com.mindspark.compose.core.network

import com.mindspark.compose.core.auth.AuthService
import com.mindspark.compose.core.config.EnvConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

fun buildOkHttp(auth: AuthService): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(auth))
        .authenticator(TokenAuthenticator(auth))
        .build()
}

fun buildRetrofit(env: EnvConfig, client: OkHttpClient): Retrofit {
    val json = Json { ignoreUnknownKeys = true }
    val contentType = "application/json".toMediaType()
    val baseUrl = if (env.apiUrl.endsWith("/")) env.apiUrl else "${env.apiUrl}/"
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory(contentType))
        .build()
}
