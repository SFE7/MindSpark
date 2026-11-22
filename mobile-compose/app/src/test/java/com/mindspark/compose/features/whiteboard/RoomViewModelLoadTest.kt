package com.mindspark.compose.features.whiteboard

import com.mindspark.compose.features.rooms.data.RoomRepository
import com.mindspark.compose.features.rooms.model.Room
import com.mindspark.compose.features.whiteboard.data.CategoryRepository
import com.mindspark.compose.features.whiteboard.data.DrawingRepository
import com.mindspark.compose.features.whiteboard.data.DrawingRequest
import com.mindspark.compose.features.whiteboard.data.IdeaPayload
import com.mindspark.compose.features.whiteboard.data.IdeaRepository
import com.mindspark.compose.features.whiteboard.model.Category
import com.mindspark.compose.features.whiteboard.model.DrawingDto
import com.mindspark.compose.features.whiteboard.model.Idea
import com.mindspark.compose.features.whiteboard.ui.RoomUiState
import com.mindspark.compose.features.whiteboard.ui.RoomViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelLoadTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeRoomRepository : RoomRepository {
        var failRoom: Boolean = false
        override suspend fun listRooms(): List<Room> = emptyList()
        override suspend fun createRoom(name: String): Room = throw NotImplementedError()
        override suspend fun getRoom(id: String): Room {
            if (failRoom) throw IOException("Failed to load room")
            return Room(id = id, name = "Test Room", invitationCode = "INV123")
        }
        override suspend fun renameRoom(id: String, name: String): Room = throw NotImplementedError()
        override suspend fun joinByCode(code: String): Room = throw NotImplementedError()
        override suspend fun reopenRoom(id: String): Room = throw NotImplementedError()
        override suspend fun closeRoom(id: String): Room = throw NotImplementedError()
        override suspend fun openRoom(id: String): Room = throw NotImplementedError()
        override suspend fun leaveRoom(id: String) {}
        override suspend fun removeMember(id: String, memberId: Long) {}
        override suspend fun deleteRoom(id: String) {}
        override suspend fun sendHeartbeat(roomId: String, clientId: String) {}
        override suspend fun sendLeave(roomId: String, clientId: String) {}
    }

    private class FakeIdeaRepository : IdeaRepository {
        override suspend fun list(roomId: String, categoryId: Long?, search: String, sortBy: String): List<Idea> =
            listOf(Idea(id = "i1", title = "Idea 1"))
        override suspend fun create(roomId: String, payload: IdeaPayload): Idea = throw NotImplementedError()
        override suspend fun update(id: String, payload: IdeaPayload): Idea = throw NotImplementedError()
        override suspend fun delete(id: String) {}
        override suspend fun vote(id: String): Idea = throw NotImplementedError()
        override suspend fun removeVote(id: String): Idea = throw NotImplementedError()
    }

    private class FakeCategoryRepository : CategoryRepository {
        override suspend fun list(roomId: String): List<Category> =
            listOf(Category(id = 1L, title = "Cat 1"))
        override suspend fun create(roomId: String, title: String): Category = throw NotImplementedError()
        override suspend fun rename(id: Long, title: String): Category = throw NotImplementedError()
        override suspend fun delete(id: Long) {}
    }

    private class FakeDrawingRepository : DrawingRepository {
        override suspend fun list(roomId: String): List<DrawingDto> =
            listOf(DrawingDto(id = "d1", kind = "pen", payload = "[]"))
        override suspend fun create(roomId: String, request: DrawingRequest): DrawingDto = throw NotImplementedError()
        override suspend fun move(roomId: String, id: String, offsetX: Float, offsetY: Float): DrawingDto = throw NotImplementedError()
        override suspend fun delete(roomId: String, id: String) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsRoomAndBecomesReadyWithAllLists() = runTest {
        val roomRepo = FakeRoomRepository()
        val ideaRepo = FakeIdeaRepository()
        val categoryRepo = FakeCategoryRepository()
        val drawingRepo = FakeDrawingRepository()

        val viewModel = RoomViewModel(
            roomId = "r1",
            roomRepository = roomRepo,
            ideaRepository = ideaRepo,
            categoryRepository = categoryRepo,
            drawingRepository = drawingRepo
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Ready but was $state", state is RoomUiState.Ready)
        val ready = state as RoomUiState.Ready
        assertEquals("r1", ready.room.id)
        assertEquals(1, ready.canvas.ideas.size)
        assertEquals(1, ready.canvas.categories.size)
        assertEquals(1, ready.canvas.elements.size)
    }

    @Test
    fun failureInRoomCallSetsErrorState() = runTest {
        val roomRepo = FakeRoomRepository().apply { failRoom = true }
        val ideaRepo = FakeIdeaRepository()
        val categoryRepo = FakeCategoryRepository()
        val drawingRepo = FakeDrawingRepository()

        val viewModel = RoomViewModel(
            roomId = "r1",
            roomRepository = roomRepo,
            ideaRepository = ideaRepo,
            categoryRepository = categoryRepo,
            drawingRepository = drawingRepo
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("State should be Error but was $state", state is RoomUiState.Error)
    }
}
