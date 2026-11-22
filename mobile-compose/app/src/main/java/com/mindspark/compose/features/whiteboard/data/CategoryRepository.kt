package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.Category

interface CategoryRepository {
    suspend fun list(roomId: String): List<Category>
    suspend fun create(roomId: String, title: String): Category
    suspend fun rename(id: Long, title: String): Category
    suspend fun delete(id: Long)
}

class NetworkCategoryRepository(private val api: CategoryApi) : CategoryRepository {

    override suspend fun list(roomId: String): List<Category> = api.list(roomId)

    override suspend fun create(roomId: String, title: String): Category =
        api.create(roomId, CategoryTitleRequest(title))

    override suspend fun rename(id: Long, title: String): Category =
        api.rename(id, CategoryTitleRequest(title))

    override suspend fun delete(id: Long) {
        api.delete(id)
    }
}
