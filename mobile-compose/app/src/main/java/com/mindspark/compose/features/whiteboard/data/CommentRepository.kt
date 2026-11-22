package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.CommentItem

interface CommentRepository {
    suspend fun list(ideaId: String): List<CommentItem>
    suspend fun add(ideaId: String, text: String): CommentItem
    suspend fun reply(commentId: String, text: String): CommentItem
    suspend fun edit(id: String, text: String): CommentItem
    suspend fun delete(id: String)
}

class NetworkCommentRepository(private val api: CommentApi) : CommentRepository {

    override suspend fun list(ideaId: String): List<CommentItem> = api.list(ideaId)

    override suspend fun add(ideaId: String, text: String): CommentItem =
        api.add(ideaId, AddCommentRequest(content = text, text = text))

    override suspend fun reply(commentId: String, text: String): CommentItem =
        api.reply(commentId, ReplyRequest(content = text))

    override suspend fun edit(id: String, text: String): CommentItem =
        api.edit(id, EditCommentRequest(content = text))

    override suspend fun delete(id: String) {
        api.delete(id)
    }
}
