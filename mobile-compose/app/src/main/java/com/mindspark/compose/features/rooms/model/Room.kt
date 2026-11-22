package com.mindspark.compose.features.rooms.model

import com.mindspark.compose.core.network.UserDto
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: String,
    val name: String,
    val invitationCode: String,
    val owner: UserDto? = null,
    val createdAt: String? = null,
    val members: List<UserDto> = emptyList(),
    val memberCount: Int = 0,
    val member: Boolean = false,
    val closed: Boolean = false,
    val offline: Boolean = false
)
