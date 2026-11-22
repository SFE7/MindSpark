package com.mindspark.compose.features.whiteboard.data

import kotlinx.serialization.Serializable

@Serializable
data class IdeaPayload(
    val title: String = "",
    val description: String? = null,
    val text: String = title,
    val x: Double? = null,
    val y: Double? = null,
    val color: String = "#273b3b",
    val categoryId: Long? = null
)
