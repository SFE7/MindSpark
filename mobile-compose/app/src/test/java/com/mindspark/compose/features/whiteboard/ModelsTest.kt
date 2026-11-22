package com.mindspark.compose.features.whiteboard

import androidx.compose.ui.graphics.Color
import com.mindspark.compose.TestFixtures
import com.mindspark.compose.features.whiteboard.logic.colorFromHex
import com.mindspark.compose.features.whiteboard.model.Category
import com.mindspark.compose.features.whiteboard.model.CommentItem
import com.mindspark.compose.features.whiteboard.model.DrawingDto
import com.mindspark.compose.features.whiteboard.model.Idea
import com.mindspark.compose.features.whiteboard.model.ShapeElement
import com.mindspark.compose.features.whiteboard.model.StrokeElement
import com.mindspark.compose.features.whiteboard.model.UnknownElement
import com.mindspark.compose.features.whiteboard.model.toElement
import com.mindspark.compose.features.whiteboard.model.toRequest
import com.mindspark.compose.ui.theme.Ink
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesIdeaFixture() {
        val raw = TestFixtures.fixture("idea.json")
        val idea = json.decodeFromString<Idea>(raw)

        assertEquals("8fa7b2c1-2345-4a67-8901-bcdef0123456", idea.id)
        assertEquals("Unified State Flow", idea.title)
        assertEquals(250.0, idea.x!!, 0.001)
        assertEquals(180.0, idea.y!!, 0.001)
        assertEquals(3L, idea.voteCount)
        assertTrue(idea.voted)
        assertEquals(10L, idea.category?.id)
        assertNotNull(idea.author)
        assertNotNull(idea.room)
    }

    @Test
    fun parsesIdeaWithNullCoordinates() {
        val raw = """{"id":"i-null","title":"Draft","x":null,"y":null}"""
        val idea = json.decodeFromString<Idea>(raw)
        assertEquals("i-null", idea.id)
        assertNull(idea.x)
        assertNull(idea.y)
    }

    @Test
    fun parsesCategoryFixture() {
        val raw = TestFixtures.fixture("category.json")
        val cat = json.decodeFromString<Category>(raw)

        assertEquals(10L, cat.id)
        assertEquals("Architecture", cat.title)
    }

    @Test
    fun parsesCommentWithRepliesFixture() {
        val raw = TestFixtures.fixture("comment_with_replies.json")
        val comment = json.decodeFromString<CommentItem>(raw)

        assertEquals("c1111111-2222-3333-4444-555555555555", comment.id)
        assertNull(comment.parentId)
        assertEquals(1, comment.replies.size)
        assertEquals("c2222222-3333-4444-5555-666666666666", comment.replies[0].id)
    }

    @Test
    fun drawingRoundTripsTheFiveFixtures() {
        val fixtures = listOf(
            "drawing_pen.json",
            "drawing_highlighter.json",
            "drawing_rectangle.json",
            "drawing_circle.json",
            "drawing_triangle.json"
        )

        for (name in fixtures) {
            val raw = TestFixtures.fixture(name)
            val dto = json.decodeFromString<DrawingDto>(raw)
            val element = dto.toElement()

            assertFalse("$name parsed as UnknownElement", element is UnknownElement)

            val request = element.toRequest()
            assertEquals("$name kind mismatch", dto.kind, request.kind)
            assertEquals("$name color mismatch", dto.color, request.color)
            assertEquals("$name width mismatch", dto.width, request.width, 0.01f)
            assertEquals("$name opacity mismatch", dto.opacity, request.opacity, 0.01f)

            // When re-parsed, geometry must match
            val roundTripDto = DrawingDto(
                id = dto.id,
                roomId = dto.roomId,
                author = dto.author,
                kind = request.kind,
                color = request.color,
                width = request.width,
                opacity = request.opacity,
                payload = request.payload,
                offsetX = request.offsetX,
                offsetY = request.offsetY
            )
            val roundTripElement = roundTripDto.toElement()

            if (element is StrokeElement) {
                assertTrue(roundTripElement is StrokeElement)
                val origStroke = element
                val rtStroke = roundTripElement as StrokeElement
                assertEquals(origStroke.points.size, rtStroke.points.size)
                for (i in origStroke.points.indices) {
                    assertEquals(origStroke.points[i].x, rtStroke.points[i].x, 0.01f)
                    assertEquals(origStroke.points[i].y, rtStroke.points[i].y, 0.01f)
                }
            } else if (element is ShapeElement) {
                assertTrue(roundTripElement is ShapeElement)
                val origShape = element
                val rtShape = roundTripElement as ShapeElement
                assertEquals(origShape.x, rtShape.x, 0.01f)
                assertEquals(origShape.y, rtShape.y, 0.01f)
                assertEquals(origShape.width, rtShape.width, 0.01f)
                assertEquals(origShape.height, rtShape.height, 0.01f)
            }
        }
    }

    @Test
    fun unknownKindBecomesUnknownElement() {
        val dto = DrawingDto(id = "d-hex", kind = "hexagon", payload = "{}")
        val el = dto.toElement()
        assertTrue(el is UnknownElement)
        assertEquals("d-hex", el.id)
    }

    @Test
    fun invalidJsonPayloadBecomesUnknownElement() {
        val dto = DrawingDto(id = "d-bad", kind = "pen", payload = "not json")
        val el = dto.toElement()
        assertTrue(el is UnknownElement)
        assertEquals("d-bad", el.id)
    }

    @Test
    fun colorFromHexConvertsValidAndFallsBackToInk() {
        val red = colorFromHex("#ff0000")
        assertEquals(Color.Red.copy(alpha = 1f), red)

        val bad = colorFromHex("nope")
        assertEquals(Ink, bad)
    }
}
