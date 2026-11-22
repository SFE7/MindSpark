package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.Idea

interface IdeaRepository {
    suspend fun list(
        roomId: String,
        categoryId: Long? = null,
        search: String = "",
        sortBy: String = "createdAt"
    ): List<Idea>

    suspend fun create(roomId: String, payload: IdeaPayload): Idea
    suspend fun update(id: String, payload: IdeaPayload): Idea
    suspend fun delete(id: String)
    suspend fun vote(id: String): Idea
    suspend fun removeVote(id: String): Idea
}

class NetworkIdeaRepository(private val api: IdeaApi) : IdeaRepository {

    override suspend fun list(
        roomId: String,
        categoryId: Long?,
        search: String,
        sortBy: String
    ): List<Idea> = api.list(roomId, categoryId, search, sortBy)

    override suspend fun create(roomId: String, payload: IdeaPayload): Idea =
        api.create(roomId, payload)

    override suspend fun update(id: String, payload: IdeaPayload): Idea =
        api.update(id, payload)

    override suspend fun delete(id: String) {
        api.delete(id)
    }

    override suspend fun vote(id: String): Idea = api.vote(id)

    override suspend fun removeVote(id: String): Idea = api.removeVote(id)
}
