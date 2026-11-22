package com.mindspark.compose.features.rooms

import com.mindspark.compose.TestFixtures
import com.mindspark.compose.features.rooms.model.Room
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RoomModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesSingleRoomFixture() {
        val raw = TestFixtures.fixture("room.json")
        val room = json.decodeFromString<Room>(raw)

        assertEquals("3ddff759-1668-4d92-939b-a05698d5b9f8", room.id)
        assertEquals("Design Sprint", room.name)
        assertEquals("ABC12345", room.invitationCode)
        assertNotNull(room.owner)
        assertEquals(1L, room.owner?.id)
        assertEquals("ilyes", room.owner?.username)
        assertNull(room.owner?.email)
        assertEquals(2, room.members.size)
        assertEquals(2, room.memberCount)
        assertTrue(room.member)
        assertFalse(room.closed)
        assertFalse(room.offline)
    }

    @Test
    fun parsesRoomsListFixture() {
        val raw = TestFixtures.fixture("rooms_list.json")
        val rooms = json.decodeFromString<List<Room>>(raw)

        assertFalse(rooms.isEmpty())
        val first = rooms.first()
        assertEquals("3ddff759-1668-4d92-939b-a05698d5b9f8", first.id)
    }
}
