package com.mindspark.compose.features.whiteboard

import androidx.compose.ui.geometry.Offset
import com.mindspark.compose.features.whiteboard.logic.ViewportState
import com.mindspark.compose.features.whiteboard.logic.clampScale
import com.mindspark.compose.features.whiteboard.logic.defaultIdeaCentre
import com.mindspark.compose.features.whiteboard.logic.fitScale
import com.mindspark.compose.features.whiteboard.logic.ideaTopLeft
import com.mindspark.compose.features.whiteboard.logic.screenToWorld
import com.mindspark.compose.features.whiteboard.logic.zoomAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportMathTest {

    @Test
    fun screenToWorldTransformsCorrectly() {
        val vp = ViewportState(scale = 2f, translation = Offset(100f, 50f))
        val world = screenToWorld(Offset(300f, 250f), vp, density = 1f)
        assertEquals(100f, world.x, 0.001f)
        assertEquals(100f, world.y, 0.001f)

        val density = 2.75f
        val screen = Offset(100f * 2f * density + 100f, 100f * 2f * density + 50f)
        val worldWithDensity = screenToWorld(screen, vp, density = density)
        assertEquals(100f, worldWithDensity.x, 0.001f)
        assertEquals(100f, worldWithDensity.y, 0.001f)
    }

    @Test
    fun clampScaleLimitsZoom() {
        assertEquals(0.3f, clampScale(0.1f), 0.001f)
        assertEquals(4.0f, clampScale(9.0f), 0.001f)
        assertEquals(1.5f, clampScale(1.5f), 0.001f)
    }

    @Test
    fun fitScaleComputesExpectedValue() {
        val fit = fitScale(screenWidthPx = 1080f, density = 2.75f)
        assertEquals(0.3927f, fit, 0.01f)
    }

    @Test
    fun ideaTopLeftCentersCard() {
        val topLeft = ideaTopLeft(500f, 250f)
        assertEquals(395f, topLeft.x, 0.001f)
        assertEquals(195f, topLeft.y, 0.001f)
    }

    @Test
    fun defaultIdeaCentreStaysInsideWorldBoundsForManyIdeas() {
        for (i in 0 until 50) {
            val centre = defaultIdeaCentre(i)
            assertTrue("x ${centre.x} should be >= 30", centre.x >= 30f)
            assertTrue("x ${centre.x} should be <= 970", centre.x <= 970f)
            assertTrue("y ${centre.y} should be >= 20", centre.y >= 20f)
            assertTrue("y ${centre.y} should be <= 480", centre.y <= 480f)
        }
    }

    @Test
    fun zoomAroundKeepsFocusPointWorldPositionFixed() {
        val vp = ViewportState(scale = 1f, translation = Offset(50f, 50f))
        val focusScreen = Offset(400f, 300f)
        val density = 2f

        val worldBefore = screenToWorld(focusScreen, vp, density)
        val zoomedVp = zoomAround(vp, focusScreen, factor = 1.5f)
        val worldAfter = screenToWorld(focusScreen, zoomedVp, density)

        assertEquals(worldBefore.x, worldAfter.x, 0.01f)
        assertEquals(worldBefore.y, worldAfter.y, 0.01f)
    }
}
