package com.mindspark.compose.features.whiteboard

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mindspark.compose.features.whiteboard.model.ShapeElement
import com.mindspark.compose.features.whiteboard.model.StrokeElement
import com.mindspark.compose.features.whiteboard.model.UnknownElement
import com.mindspark.compose.features.whiteboard.model.WorldPoint
import com.mindspark.compose.features.whiteboard.ui.widgets.CanvasViewport
import com.mindspark.compose.features.whiteboard.ui.widgets.DrawingLayer
import com.mindspark.compose.features.whiteboard.ui.widgets.IdeaCard
import com.mindspark.compose.features.whiteboard.model.Idea
import com.mindspark.compose.features.whiteboard.logic.ViewportState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingLayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersAllElementKindsAndEdgeCasesWithoutThrowing() {
        val elements = listOf(
            // 3-point stroke
            StrokeElement(
                id = "s1",
                authorId = 1L,
                tool = "pen",
                color = "#273b3b",
                width = 5f,
                opacity = 1f,
                points = listOf(WorldPoint(10f, 10f), WorldPoint(20f, 20f), WorldPoint(30f, 30f)),
                offsetX = 0f,
                offsetY = 0f
            ),
            // highlighter
            StrokeElement(
                id = "s2",
                authorId = 1L,
                tool = "highlighter",
                color = "#f3c96b",
                width = 30f,
                opacity = 0.35f,
                points = listOf(WorldPoint(50f, 50f), WorldPoint(100f, 50f)),
                offsetX = 0f,
                offsetY = 0f
            ),
            // One-point stroke (RF4)
            StrokeElement(
                id = "s3",
                authorId = 1L,
                tool = "pen",
                color = "#ed8d74",
                width = 5f,
                opacity = 1f,
                points = listOf(WorldPoint(150f, 150f)),
                offsetX = 0f,
                offsetY = 0f
            ),
            // Two identical points stroke
            StrokeElement(
                id = "s4",
                authorId = 1L,
                tool = "pen",
                color = "#9fc9a9",
                width = 5f,
                opacity = 1f,
                points = listOf(WorldPoint(200f, 200f), WorldPoint(200f, 200f)),
                offsetX = 0f,
                offsetY = 0f
            ),
            // Shapes: rectangle, circle, triangle
            ShapeElement(
                id = "sh1",
                authorId = 1L,
                kind = "rectangle",
                color = "#9dc7d8",
                x = 10f,
                y = 10f,
                width = 60f,
                height = 40f,
                offsetX = 0f,
                offsetY = 0f,
                strokeWidth = 2f,
                opacity = 1f
            ),
            // Zero-size shape (RF4)
            ShapeElement(
                id = "sh2",
                authorId = 1L,
                kind = "rectangle",
                color = "#9dc7d8",
                x = 10f,
                y = 10f,
                width = 0f,
                height = 0f,
                offsetX = 0f,
                offsetY = 0f,
                strokeWidth = 2f,
                opacity = 1f
            ),
            ShapeElement(
                id = "sh3",
                authorId = 1L,
                kind = "circle",
                color = "#b9add5",
                x = 100f,
                y = 100f,
                width = 50f,
                height = 50f,
                offsetX = 0f,
                offsetY = 0f,
                strokeWidth = 2f,
                opacity = 1f
            ),
            ShapeElement(
                id = "sh4",
                authorId = 1L,
                kind = "triangle",
                color = "#273b3b",
                x = 200f,
                y = 100f,
                width = 50f,
                height = 50f,
                offsetX = 0f,
                offsetY = 0f,
                strokeWidth = 2f,
                opacity = 1f
            ),
            // Unknown element (RF4)
            UnknownElement(id = "u1")
        )

        composeTestRule.setContent {
            CanvasViewport(
                viewport = ViewportState(scale = 1f, translation = Offset.Zero),
                onViewportChange = {}
            ) {
                DrawingLayer(elements = elements)
                IdeaCard(
                    idea = Idea(id = "i1", title = "Centered Idea", x = 500.0, y = 250.0),
                    gesturesEnabled = false
                )
            }
        }

        composeTestRule.waitForIdle()
    }
}
