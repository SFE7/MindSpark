package com.mindspark.compose.features.whiteboard.model

import com.mindspark.compose.core.network.UserDto
import kotlinx.serialization.Serializable

@Serializable
data class CommentItem(
    val id: String = "",
    val ideaId: String = "",
    val author: UserDto? = null,
    val content: String = "",
    val text: String = "",
    val parentId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val replies: List<CommentItem> = emptyList()
)
