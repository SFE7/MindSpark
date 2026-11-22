package com.mindspark.compose.features.rooms.data

import com.mindspark.compose.features.rooms.model.Room

interface RoomRepository {
    suspend fun listRooms(): List<Room>
    suspend fun getRoom(id: String): Room
    suspend fun createRoom(name: String): Room
    suspend fun joinByCode(code: String): Room
    suspend fun reopenRoom(id: String): Room
    suspend fun renameRoom(id: String, name: String): Room
    suspend fun closeRoom(id: String): Room
    suspend fun openRoom(id: String): Room
    suspend fun leaveRoom(id: String)
    suspend fun removeMember(roomId: String, memberId: Long)
    suspend fun deleteRoom(id: String)
    suspend fun sendHeartbeat(roomId: String, clientId: String)
    suspend fun sendLeave(roomId: String, clientId: String)
}

class NetworkRoomRepository(private val api: RoomApi) : RoomRepository {
    override suspend fun listRooms(): List<Room> = api.listRooms()
    override suspend fun getRoom(id: String): Room = api.getRoom(id)
    override suspend fun createRoom(name: String): Room = api.createRoom(CreateRoomRequest(name))
    override suspend fun joinByCode(code: String): Room = api.joinByCode(JoinRoomRequest(code))
    override suspend fun reopenRoom(id: String): Room = api.reopenRoom(id)
    override suspend fun renameRoom(id: String, name: String): Room = api.renameRoom(id, RenameRoomRequest(name))
    override suspend fun closeRoom(id: String): Room = api.closeRoom(id)
    override suspend fun openRoom(id: String): Room = api.openRoom(id)
    override suspend fun leaveRoom(id: String) { api.leaveRoom(id) }
    override suspend fun removeMember(roomId: String, memberId: Long) { api.removeMember(roomId, memberId) }
    override suspend fun deleteRoom(id: String) { api.deleteRoom(id) }
    override suspend fun sendHeartbeat(roomId: String, clientId: String) { api.sendHeartbeat(roomId, PresenceRequest(clientId)) }
    override suspend fun sendLeave(roomId: String, clientId: String) { api.sendLeave(roomId, PresenceRequest(clientId)) }
}
