package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.CommentItem
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class AddCommentRequest(
    val content: String,
    val text: String,
    val parentId: String? = null
)

@Serializable
data class ReplyRequest(val content: String)

@Serializable
data class EditCommentRequest(val content: String)

interface CommentApi {

    @GET("api/ideas/{ideaId}/comments")
    suspend fun list(@Path("ideaId") ideaId: String): List<CommentItem>

    @POST("api/ideas/{ideaId}/comments")
    suspend fun add(
        @Path("ideaId") ideaId: String,
        @Body request: AddCommentRequest
    ): CommentItem

    @POST("api/comments/{commentId}/replies")
    suspend fun reply(
        @Path("commentId") commentId: String,
        @Body request: ReplyRequest
    ): CommentItem

    @PUT("api/comments/{commentId}")
    suspend fun edit(
        @Path("commentId") commentId: String,
        @Body request: EditCommentRequest
    ): CommentItem

    @DELETE("api/comments/{commentId}")
    suspend fun delete(@Path("commentId") commentId: String): Response<Unit>
}
