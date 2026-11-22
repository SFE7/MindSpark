package com.mindspark.compose.features.whiteboard.ui

import com.mindspark.compose.features.rooms.model.Room
import com.mindspark.compose.features.whiteboard.model.Category
import com.mindspark.compose.features.whiteboard.model.DrawingElement
import com.mindspark.compose.features.whiteboard.model.Idea

data class CanvasUiState(
    val ideas: List<Idea> = emptyList(),
    val elements: List<DrawingElement> = emptyList(),
    val categories: List<Category> = emptyList()
)

sealed interface RoomUiState {
    data object Loading : RoomUiState
    data class Ready(val canvas: CanvasUiState, val room: Room) : RoomUiState
    data class Error(val message: String) : RoomUiState
}
