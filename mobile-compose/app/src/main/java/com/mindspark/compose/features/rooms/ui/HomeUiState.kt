package com.mindspark.compose.features.rooms.ui

import com.mindspark.compose.features.rooms.model.Room

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val rooms: List<Room>) : HomeUiState
    data class Error(val message: String, val isConnection: Boolean) : HomeUiState
}
