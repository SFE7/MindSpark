package com.mindspark.compose.features.whiteboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindspark.compose.MindSparkApplication
import com.mindspark.compose.core.network.httpCode
import com.mindspark.compose.core.network.isConnectionError
import com.mindspark.compose.features.rooms.data.RoomRepository
import com.mindspark.compose.features.rooms.model.Room
import com.mindspark.compose.features.whiteboard.data.CategoryRepository
import com.mindspark.compose.features.whiteboard.data.DrawingRepository
import com.mindspark.compose.features.whiteboard.data.IdeaRepository
import com.mindspark.compose.features.whiteboard.model.Category
import com.mindspark.compose.features.whiteboard.model.DrawingDto
import com.mindspark.compose.features.whiteboard.model.DrawingElement
import com.mindspark.compose.features.whiteboard.model.Idea
import com.mindspark.compose.features.whiteboard.model.toElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class RoomViewModel(
    val roomId: String,
    private val roomRepository: RoomRepository,
    private val ideaRepository: IdeaRepository,
    private val categoryRepository: CategoryRepository,
    private val drawingRepository: DrawingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RoomUiState>(RoomUiState.Loading)
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    init {
        loadRoomData()
    }

    fun loadRoomData() {
        viewModelScope.launch {
            _uiState.value = RoomUiState.Loading
            try {
                coroutineScope {
                    val roomDeferred = async { roomRepository.getRoom(roomId) }
                    val ideasDeferred = async { ideaRepository.list(roomId) }
                    val categoriesDeferred = async { categoryRepository.list(roomId) }
                    val drawingsDeferred = async { drawingRepository.list(roomId) }

                    val room = roomDeferred.await()
                    val ideas = ideasDeferred.await()
                    val categories = categoriesDeferred.await()
                    val drawings = drawingsDeferred.await()

                    val elements = drawings.map { it.toElement() }

                    _uiState.value = RoomUiState.Ready(
                        canvas = CanvasUiState(
                            ideas = ideas,
                            elements = elements,
                            categories = categories
                        ),
                        room = room
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _uiState.value = RoomUiState.Error(e.message() ?: "Server error (${e.code()})")
            } catch (e: IOException) {
                _uiState.value = RoomUiState.Error("Network error: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = RoomUiState.Error(e.message ?: "Failed to load room")
            }
        }
    }

    companion object {
        fun provideFactory(roomId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MindSparkApplication
                val container = app.container
                RoomViewModel(
                    roomId = roomId,
                    roomRepository = container.roomRepository,
                    ideaRepository = container.ideaRepository,
                    categoryRepository = container.categoryRepository,
                    drawingRepository = container.drawingRepository
                )
            }
        }
    }
}
