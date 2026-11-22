package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.Idea
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface IdeaApi {

    @GET("api/rooms/{roomId}/ideas")
    suspend fun list(
        @Path("roomId") roomId: String,
        @Query("categoryId") categoryId: Long?,
        @Query("search") search: String,
        @Query("sortBy") sortBy: String
    ): List<Idea>

    @POST("api/rooms/{roomId}/ideas")
    suspend fun create(
        @Path("roomId") roomId: String,
        @Body payload: IdeaPayload
    ): Idea

    @PUT("api/ideas/{ideaId}")
    suspend fun update(
        @Path("ideaId") ideaId: String,
        @Body payload: IdeaPayload
    ): Idea

    @DELETE("api/ideas/{ideaId}")
    suspend fun delete(@Path("ideaId") ideaId: String): Response<Unit>

    @POST("api/ideas/{ideaId}/vote")
    suspend fun vote(@Path("ideaId") ideaId: String): Idea

    @DELETE("api/ideas/{ideaId}/vote")
    suspend fun removeVote(@Path("ideaId") ideaId: String): Idea
}
