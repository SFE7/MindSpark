package com.mindspark.compose.features.whiteboard.data

import com.mindspark.compose.features.whiteboard.model.DrawingDto

interface DrawingRepository {
    suspend fun list(roomId: String): List<DrawingDto>
    suspend fun create(roomId: String, request: DrawingRequest): DrawingDto
    suspend fun move(roomId: String, id: String, offsetX: Float, offsetY: Float): DrawingDto
    suspend fun delete(roomId: String, id: String)
}

class NetworkDrawingRepository(private val api: DrawingApi) : DrawingRepository {

    override suspend fun list(roomId: String): List<DrawingDto> =
        api.list(roomId)

    override suspend fun create(roomId: String, request: DrawingRequest): DrawingDto =
        api.create(roomId, request)

    override suspend fun move(
        roomId: String,
        id: String,
        offsetX: Float,
        offsetY: Float
    ): DrawingDto =
        api.move(roomId, id, DrawingMoveRequest(offsetX, offsetY))

    override suspend fun delete(roomId: String, id: String) {
        api.delete(roomId, id)
    }
}
