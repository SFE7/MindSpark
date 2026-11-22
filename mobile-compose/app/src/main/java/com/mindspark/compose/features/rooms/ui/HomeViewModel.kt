package com.mindspark.compose.features.rooms.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindspark.compose.MindSparkApplication
import com.mindspark.compose.features.rooms.data.RoomRepository
import com.mindspark.compose.features.rooms.model.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class HomeViewModel(private val repository: RoomRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _navigateToRoom = MutableStateFlow<String?>(null)
    val navigateToRoom: StateFlow<String?> = _navigateToRoom.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val rooms = repository.listRooms()
                _uiState.value = HomeUiState.Success(rooms)
            } catch (e: HttpException) {
                _uiState.value = HomeUiState.Error(e.message ?: "Failed to load rooms", isConnection = false)
            } catch (e: IOException) {
                _uiState.value = HomeUiState.Error(e.message ?: "Network error", isConnection = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unexpected error", isConnection = false)
            }
        }
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val room = repository.createRoom(trimmed)
                val current = (_uiState.value as? HomeUiState.Success)?.rooms ?: emptyList()
                _uiState.value = HomeUiState.Success(current + room)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Failed to create room"
            }
        }
    }

    fun join(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                val room = repository.joinByCode(trimmed)
                val current = (_uiState.value as? HomeUiState.Success)?.rooms ?: emptyList()
                _uiState.value = HomeUiState.Success(current + room)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Failed to join room"
            }
        }
    }

    fun open(room: Room) {
        viewModelScope.launch {
            try {
                repository.reopenRoom(room.id)
                _navigateToRoom.value = room.id
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Failed to open room"
            }
        }
    }

    fun delete(room: Room) {
        viewModelScope.launch {
            try {
                repository.deleteRoom(room.id)
                val current = (_uiState.value as? HomeUiState.Success)?.rooms ?: emptyList()
                _uiState.value = HomeUiState.Success(current.filter { it.id != room.id })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Failed to delete room"
            }
        }
    }

    fun navigated() {
        _navigateToRoom.value = null
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MindSparkApplication)
                HomeViewModel(app.container.roomRepository)
            }
        }
    }
}
