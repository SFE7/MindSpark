package com.mindspark.compose.features.whiteboard.model

import com.mindspark.compose.features.whiteboard.data.DrawingRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class WorldPoint(val x: Float, val y: Float)

sealed interface DrawingElement {
    val id: String
}

data class StrokeElement(
    override val id: String,
    val authorId: Long?,
    val tool: String,
    val color: String,
    val width: Float,
    val opacity: Float,
    val points: List<WorldPoint>,
    val offsetX: Float,
    val offsetY: Float
) : DrawingElement

data class ShapeElement(
    override val id: String,
    val authorId: Long?,
    val kind: String,
    val color: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float,
    val strokeWidth: Float,
    val opacity: Float
) : DrawingElement

data class UnknownElement(override val id: String) : DrawingElement

private val payloadJson = Json { ignoreUnknownKeys = true }

private val STROKE_KINDS = setOf("pen", "highlighter")
private val SHAPE_KINDS = setOf("rectangle", "circle", "triangle")

fun DrawingDto.toElement(): DrawingElement {
    return try {
        when (kind) {
            in STROKE_KINDS -> {
                val parsed = payloadJson.parseToJsonElement(payload)
                val pointsArray: JsonArray = when {
                    parsed is JsonArray -> parsed
                    parsed is JsonObject && parsed.containsKey("points") ->
                        parsed["points"]!!.jsonArray
                    else -> return UnknownElement(id)
                }
                val points = pointsArray.map { p ->
                    val obj = p.jsonObject
                    WorldPoint(
                        x = obj["x"]!!.jsonPrimitive.float,
                        y = obj["y"]!!.jsonPrimitive.float
                    )
                }
                StrokeElement(
                    id = id,
                    authorId = author?.id,
                    tool = kind,
                    color = color,
                    width = width,
                    opacity = opacity,
                    points = points,
                    offsetX = offsetX,
                    offsetY = offsetY
                )
            }
            in SHAPE_KINDS -> {
                val obj = payloadJson.parseToJsonElement(payload).jsonObject
                ShapeElement(
                    id = id,
                    authorId = author?.id,
                    kind = kind,
                    color = color,
                    x = obj["x"]?.jsonPrimitive?.float ?: 0f,
                    y = obj["y"]?.jsonPrimitive?.float ?: 0f,
                    width = obj["width"]?.jsonPrimitive?.float ?: 50f,
                    height = obj["height"]?.jsonPrimitive?.float ?: 50f,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    strokeWidth = width,
                    opacity = opacity
                )
            }
            else -> UnknownElement(id)
        }
    } catch (e: Exception) {
        UnknownElement(id)
    }
}

fun DrawingElement.toRequest(): DrawingRequest {
    return when (this) {
        is StrokeElement -> {
            val pointsJson = buildJsonArray {
                points.forEach { p ->
                    addJsonObject {
                        put("x", p.x)
                        put("y", p.y)
                    }
                }
            }
            DrawingRequest(
                id = id,
                kind = tool,
                color = color,
                width = width,
                opacity = opacity,
                payload = pointsJson.toString(),
                offsetX = offsetX,
                offsetY = offsetY
            )
        }
        is ShapeElement -> {
            val shapeJson = buildJsonObject {
                put("x", x)
                put("y", y)
                put("width", width)
                put("height", height)
            }
            DrawingRequest(
                id = id,
                kind = kind,
                color = color,
                width = strokeWidth,
                opacity = opacity,
                payload = shapeJson.toString(),
                offsetX = offsetX,
                offsetY = offsetY
            )
        }
        is UnknownElement -> DrawingRequest(
            id = id,
            kind = "unknown",
            color = "",
            width = 0f,
            opacity = 0f,
            payload = "",
            offsetX = 0f,
            offsetY = 0f
        )
    }
}
