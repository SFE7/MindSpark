package com.mindspark.compose.features.whiteboard.data

import kotlinx.serialization.Serializable

@Serializable
data class DrawingRequest(
    val id: String,
    val kind: String,
    val color: String,
    val width: Float,
    val opacity: Float,
    val payload: String,
    val offsetX: Float,
    val offsetY: Float
)

@Serializable
data class DrawingMoveRequest(val offsetX: Float, val offsetY: Float)
