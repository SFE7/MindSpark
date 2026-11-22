package com.mindspark.compose.features.rooms.data

import com.mindspark.compose.features.rooms.model.Room
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class CreateRoomRequest(val name: String)

@Serializable
data class RenameRoomRequest(val name: String)

@Serializable
data class JoinRoomRequest(val invitationCode: String)

@Serializable
data class PresenceRequest(val clientId: String)

interface RoomApi {
    @GET("api/rooms")
    suspend fun listRooms(): List<Room>

    @GET("api/rooms/{id}")
    suspend fun getRoom(@Path("id") id: String): Room

    @POST("api/rooms")
    suspend fun createRoom(@Body request: CreateRoomRequest): Room

    @POST("api/rooms/join")
    suspend fun joinByCode(@Body request: JoinRoomRequest): Room

    @POST("api/rooms/{id}/reopen")
    suspend fun reopenRoom(@Path("id") id: String): Room

    @PUT("api/rooms/{id}")
    suspend fun renameRoom(@Path("id") id: String, @Body request: RenameRoomRequest): Room

    @POST("api/rooms/{id}/close")
    suspend fun closeRoom(@Path("id") id: String): Room

    @POST("api/rooms/{id}/open")
    suspend fun openRoom(@Path("id") id: String): Room

    @DELETE("api/rooms/{id}/leave")
    suspend fun leaveRoom(@Path("id") id: String): Room

    @DELETE("api/rooms/{id}/members/{memberId}")
    suspend fun removeMember(@Path("id") id: String, @Path("memberId") memberId: Long): Room

    @DELETE("api/rooms/{id}")
    suspend fun deleteRoom(@Path("id") id: String): Response<Unit>

    @POST("api/rooms/{roomId}/presence")
    suspend fun sendHeartbeat(@Path("roomId") roomId: String, @Body request: PresenceRequest)

    @POST("api/rooms/{roomId}/presence/leave")
    suspend fun sendLeave(@Path("roomId") roomId: String, @Body request: PresenceRequest)
}
