package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.DrawingDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface DrawingApi {

    @GET("api/rooms/{roomId}/drawings")
    suspend fun list(@Path("roomId") roomId: String): List<DrawingDto>

    @POST("api/rooms/{roomId}/drawings")
    suspend fun create(
        @Path("roomId") roomId: String,
        @Body request: DrawingRequest
    ): DrawingDto

    @PUT("api/rooms/{roomId}/drawings/{drawingId}")
    suspend fun move(
        @Path("roomId") roomId: String,
        @Path("drawingId") drawingId: String,
        @Body request: DrawingMoveRequest
    ): DrawingDto

    @DELETE("api/rooms/{roomId}/drawings/{drawingId}")
    suspend fun delete(
        @Path("roomId") roomId: String,
        @Path("drawingId") drawingId: String
    ): Response<Unit>
}
