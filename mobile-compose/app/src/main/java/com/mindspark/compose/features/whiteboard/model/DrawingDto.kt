package com.mindspark.compose.features.whiteboard.model

import com.mindspark.compose.core.network.UserDto
import kotlinx.serialization.Serializable

@Serializable
data class DrawingDto(
    val id: String = "",
    val roomId: String = "",
    val author: UserDto? = null,
    val kind: String = "",
    val color: String = "#273b3b",
    val width: Float = 5f,
    val opacity: Float = 1f,
    val payload: String = "",
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
