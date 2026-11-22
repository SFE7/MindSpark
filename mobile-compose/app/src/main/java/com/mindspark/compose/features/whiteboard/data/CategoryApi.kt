package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.Category
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class CategoryTitleRequest(val title: String)

interface CategoryApi {

    @GET("api/rooms/{roomId}/categories")
    suspend fun list(@Path("roomId") roomId: String): List<Category>

    @POST("api/rooms/{roomId}/categories")
    suspend fun create(
        @Path("roomId") roomId: String,
        @Body request: CategoryTitleRequest
    ): Category

    @PUT("api/categories/{id}")
    suspend fun rename(
        @Path("id") id: Long,
        @Body request: CategoryTitleRequest
    ): Category

    @DELETE("api/categories/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>
}
