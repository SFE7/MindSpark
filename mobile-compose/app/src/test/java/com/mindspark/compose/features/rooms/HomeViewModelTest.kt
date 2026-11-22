package com.mindspark.compose.features.rooms

import com.mindspark.compose.features.rooms.data.RoomRepository
import com.mindspark.compose.features.rooms.model.Room
import com.mindspark.compose.features.rooms.ui.HomeUiState
import com.mindspark.compose.features.rooms.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeRoomRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeRoomRepository()
        viewModel = HomeViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshLoadsRoomsSuccessfully() = runTest(testDispatcher) {
        fakeRepo.rooms = mutableListOf(sampleRoom("1", "Room 1"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(1, (state as HomeUiState.Success).rooms.size)
    }

    @Test
    fun createAddsNewRoomToSuccessState() = runTest(testDispatcher) {
        fakeRepo.rooms = mutableListOf(sampleRoom("1", "Room 1"))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.create("Room 2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        val rooms = (state as HomeUiState.Success).rooms
        assertEquals(2, rooms.size)
        assertTrue(rooms.any { it.name == "Room 2" })
    }

    @Test
    fun joinWithBlankCodeDoesNotCallRepository() = runTest(testDispatcher) {
        viewModel.join("   ")
        advanceUntilIdle()
        assertEquals(0, fakeRepo.joinCalls)
    }

    @Test
    fun joinWithValidCodeAddsRoom() = runTest(testDispatcher) {
        fakeRepo.rooms = mutableListOf(sampleRoom("1", "Room 1"))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.join("JOIN123")
        advanceUntilIdle()

        assertEquals(1, fakeRepo.joinCalls)
        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(2, (state as HomeUiState.Success).rooms.size)
    }

    @Test
    fun failedRefreshBecomesErrorWithConnectionFlag() = runTest(testDispatcher) {
        fakeRepo.throwOnList = IOException("No internet")
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertTrue((state as HomeUiState.Error).isConnection)

        // Reset and fail with HttpException (isConnection should be false)
        fakeRepo.throwOnList = HttpException(
            Response.error<String>(500, "{}".toResponseBody("application/json".toMediaType()))
        )
        viewModel.refresh()
        advanceUntilIdle()

        val state2 = viewModel.uiState.value
        assertTrue(state2 is HomeUiState.Error)
        assertFalse((state2 as HomeUiState.Error).isConnection)
    }

    @Test
    fun failedActionKeepsCurrentSuccessListAndSetsMessage() = runTest(testDispatcher) {
        fakeRepo.rooms = mutableListOf(sampleRoom("1", "Room 1"))
        viewModel.refresh()
        advanceUntilIdle()

        fakeRepo.throwOnAction = IOException("Action failed")

        viewModel.create("Room Fail")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is HomeUiState.Success)
        assertEquals(1, (viewModel.uiState.value as HomeUiState.Success).rooms.size)
        assertNotNull(viewModel.message.value)

        viewModel.messageShown()
        assertNull(viewModel.message.value)

        viewModel.delete(sampleRoom("1", "Room 1"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is HomeUiState.Success)
        assertNotNull(viewModel.message.value)
    }

    @Test
    fun openCallsReopenAndSetsNavigateToRoom() = runTest(testDispatcher) {
        val room = sampleRoom("r1", "Sprint")
        viewModel.open(room)
        advanceUntilIdle()

        assertEquals("r1", fakeRepo.reopenedId)
        assertEquals("r1", viewModel.navigateToRoom.value)

        viewModel.navigated()
        assertNull(viewModel.navigateToRoom.value)
    }

    private fun sampleRoom(id: String, name: String) = Room(
        id = id,
        name = name,
        invitationCode = "CODE$id",
        owner = null,
        members = emptyList(),
        memberCount = 1,
        member = true,
        closed = false,
        offline = false
    )

    private class FakeRoomRepository : RoomRepository {
        var rooms = mutableListOf<Room>()
        var joinCalls = 0
        var reopenedId: String? = null
        var throwOnList: Throwable? = null
        var throwOnAction: Throwable? = null

        override suspend fun listRooms(): List<Room> {
            throwOnList?.let { throw it }
            return rooms.toList()
        }

        override suspend fun getRoom(id: String): Room = rooms.first { it.id == id }

        override suspend fun createRoom(name: String): Room {
            throwOnAction?.let { throw it }
            return Room(id = "new-${rooms.size + 1}", name = name, invitationCode = "INV")
        }

        override suspend fun joinByCode(code: String): Room {
            joinCalls++
            throwOnAction?.let { throw it }
            return Room(id = "joined-$code", name = "Joined Room", invitationCode = code)
        }

        override suspend fun reopenRoom(id: String): Room {
            reopenedId = id
            throwOnAction?.let { throw it }
            return rooms.find { it.id == id } ?: Room(id = id, name = "Reopened", invitationCode = "INV")
        }

        override suspend fun renameRoom(id: String, name: String): Room = throw NotImplementedError()
        override suspend fun closeRoom(id: String): Room = throw NotImplementedError()
        override suspend fun openRoom(id: String): Room = throw NotImplementedError()
        override suspend fun leaveRoom(id: String) {}
        override suspend fun removeMember(roomId: String, memberId: Long) {}

        override suspend fun deleteRoom(id: String) {
            throwOnAction?.let { throw it }
            rooms.removeAll { it.id == id }
        }

        override suspend fun sendHeartbeat(roomId: String, clientId: String) {}
        override suspend fun sendLeave(roomId: String, clientId: String) {}
    }
}
