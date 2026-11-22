package com.mindspark.compose.features.whiteboard.model

import com.mindspark.compose.core.network.UserDto
import com.mindspark.compose.features.rooms.model.Room
import kotlinx.serialization.Serializable

@Serializable
data class Idea(
    val id: String = "",
    val room: Room? = null,
    val author: UserDto? = null,
    val title: String = "",
    val description: String? = null,
    val text: String = "",
    val x: Double? = null,
    val y: Double? = null,
    val color: String = "#273b3b",
    val voteCount: Long = 0L,
    val voted: Boolean = false,
    val category: Category? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
