package com.mindspark.compose

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FixturesTest {

    private val expectedFixtures = listOf(
        "user.json",
        "room.json",
        "rooms_list.json",
        "idea.json",
        "category.json",
        "comment_with_replies.json",
        "drawing_pen.json",
        "drawing_highlighter.json",
        "drawing_rectangle.json",
        "drawing_circle.json",
        "drawing_triangle.json",
        "ws_auth_success.json",
        "ws_presence.json",
        "ws_ping.json",
        "ws_cursor_move.json",
        "ws_cursor_leave.json",
        "ws_card_move.json",
        "ws_stroke_points.json",
        "ws_drawing_move.json",
        "ws_object_locked.json",
        "ws_object_unlocked.json",
        "ws_idea_created.json",
        "ws_idea_updated.json",
        "ws_idea_deleted.json",
        "ws_drawing_created.json",
        "ws_drawing_updated.json",
        "ws_drawing_deleted.json",
        "ws_comment_created.json",
        "ws_comment_updated.json",
        "ws_comment_deleted.json",
        "ws_category_created.json",
        "ws_category_updated.json",
        "ws_category_deleted.json"
    )

    @Test
    fun allExpectedFixturesExistAndAreValidJson() {
        val dir = if (File("src/test/resources/fixtures").exists()) {
            File("src/test/resources/fixtures")
        } else {
            File("app/src/test/resources/fixtures")
        }

        assertTrue("Fixtures directory should exist at ${dir.absolutePath}", dir.exists() && dir.isDirectory)

        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val fileNames = files.map { it.name }.toSet()

        for (expected in expectedFixtures) {
            assertTrue("Expected fixture file missing: $expected", fileNames.contains(expected))
        }

        for (file in files) {
            val content = file.readText().trim()
            assertTrue("Fixture file is empty: ${file.name}", content.isNotEmpty())
            val element = Json.parseToJsonElement(content)
            val isValid = when (element) {
                is JsonObject -> element.isNotEmpty()
                is JsonArray -> element.isNotEmpty()
                else -> false
            }
            assertTrue("Fixture ${file.name} must be a non-empty JSON object or array", isValid)
        }
    }

    @Test
    fun testFixturesHelperLoadsResourceStream() {
        val json = fixture("user.json")
        assertTrue("Expected non-empty fixture content", json.isNotEmpty())
        val element = Json.parseToJsonElement(json)
        assertTrue("Expected JsonObject for user.json", element is JsonObject)
    }
}
