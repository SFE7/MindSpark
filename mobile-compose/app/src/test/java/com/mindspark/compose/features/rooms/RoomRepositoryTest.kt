package com.mindspark.compose.features.rooms

import com.mindspark.compose.TestFixtures
import com.mindspark.compose.features.rooms.data.NetworkRoomRepository
import com.mindspark.compose.features.rooms.data.RoomApi
import com.mindspark.compose.features.rooms.data.RoomRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RoomRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: RoomRepository

    @Before
    fun setUp() {
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(RoomApi::class.java)
        repository = NetworkRoomRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listRoomsHitsGetApiRooms() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("rooms_list.json")))
        val rooms = repository.listRooms()
        val req = server.takeRequest()

        assertEquals("GET", req.method)
        assertEquals("/api/rooms", req.path)
        assertEquals(1, rooms.size)
    }

    @Test
    fun getRoomHitsGetApiRoomsId() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        val room = repository.getRoom("r1")
        val req = server.takeRequest()

        assertEquals("GET", req.method)
        assertEquals("/api/rooms/r1", req.path)
        assertEquals("3ddff759-1668-4d92-939b-a05698d5b9f8", room.id)
    }

    @Test
    fun createRoomHitsPostApiRoomsWithNameBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody(TestFixtures.fixture("room.json")))
        val room = repository.createRoom("New Room")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms", req.path)
        assertTrue(req.body.readUtf8().contains("\"name\":\"New Room\""))
        assertNotNull(room)
    }

    @Test
    fun joinByCodeHitsPostApiRoomsJoinWithInvitationCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        val room = repository.joinByCode("ABC12345")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/join", req.path)
        assertTrue(req.body.readUtf8().contains("\"invitationCode\":\"ABC12345\""))
        assertNotNull(room)
    }

    @Test
    fun reopenRoomHitsPostApiRoomsIdReopen() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.reopenRoom("r1")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/r1/reopen", req.path)
    }

    @Test
    fun renameRoomHitsPutApiRoomsIdWithName() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.renameRoom("r1", "Renamed")
        val req = server.takeRequest()

        assertEquals("PUT", req.method)
        assertEquals("/api/rooms/r1", req.path)
        assertTrue(req.body.readUtf8().contains("\"name\":\"Renamed\""))
    }

    @Test
    fun closeRoomHitsPostApiRoomsIdClose() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.closeRoom("r1")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/r1/close", req.path)
    }

    @Test
    fun openRoomHitsPostApiRoomsIdOpen() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.openRoom("r1")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/r1/open", req.path)
    }

    @Test
    fun leaveRoomHitsDeleteApiRoomsIdLeave() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.leaveRoom("r1")
        val req = server.takeRequest()

        assertEquals("DELETE", req.method)
        assertEquals("/api/rooms/r1/leave", req.path)
    }

    @Test
    fun removeMemberHitsDeleteApiRoomsIdMembersMemberId() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TestFixtures.fixture("room.json")))
        repository.removeMember("r1", 2L)
        val req = server.takeRequest()

        assertEquals("DELETE", req.method)
        assertEquals("/api/rooms/r1/members/2", req.path)
    }

    @Test
    fun deleteRoomHitsDeleteApiRoomsId() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        repository.deleteRoom("r1")
        val req = server.takeRequest()

        assertEquals("DELETE", req.method)
        assertEquals("/api/rooms/r1", req.path)
    }

    @Test
    fun sendHeartbeatHitsPostApiRoomsIdPresence() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        repository.sendHeartbeat("r1", "c1")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/r1/presence", req.path)
        assertTrue(req.body.readUtf8().contains("\"clientId\":\"c1\""))
    }

    @Test
    fun sendLeaveHitsPostApiRoomsIdPresenceLeave() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        repository.sendLeave("r1", "c1")
        val req = server.takeRequest()

        assertEquals("POST", req.method)
        assertEquals("/api/rooms/r1/presence/leave", req.path)
        assertTrue(req.body.readUtf8().contains("\"clientId\":\"c1\""))
    }
}
