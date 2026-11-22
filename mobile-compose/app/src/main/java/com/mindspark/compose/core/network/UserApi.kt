package com.mindspark.compose.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.POST

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val email: String? = null
)

interface UserApi {
    @POST("api/users/sync")
    suspend fun sync(): UserDto
}
