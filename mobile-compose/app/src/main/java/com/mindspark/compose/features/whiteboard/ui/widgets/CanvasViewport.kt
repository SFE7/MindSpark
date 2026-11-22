package com.mindspark.compose.features.whiteboard.ui.widgets

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mindspark.compose.features.whiteboard.logic.ViewportState
import com.mindspark.compose.features.whiteboard.logic.clampScale
import com.mindspark.compose.features.whiteboard.logic.fitScale
import com.mindspark.compose.features.whiteboard.logic.zoomAround

@Composable
fun CanvasViewport(
    viewport: ViewportState,
    onViewportChange: (ViewportState) -> Unit,
    modifier: Modifier = Modifier,
    tool: String = "Hand",
    content: @Composable BoxScope.() -> Unit
) {
    val currentViewport by rememberUpdatedState(viewport)
    val density = LocalDensity.current.density
    var screenWidth = 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { screenWidth = it.width.toFloat() }
            .pointerInput(tool) {
                if (tool == "Hand") {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val zoomed = zoomAround(currentViewport, centroid, zoom)
                        val updated = zoomed.copy(
                            translation = zoomed.translation + pan
                        )
                        onViewportChange(updated)
                    }
                }
            }
    ) {
        // Inner world box — 1000 x 500 dp, top-start aligned
        Box(
            modifier = Modifier
                .requiredSize(1000.dp, 500.dp)
                .graphicsLayer(
                    scaleX = currentViewport.scale,
                    scaleY = currentViewport.scale,
                    translationX = currentViewport.translation.x,
                    translationY = currentViewport.translation.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                ),
            content = content
        )

        // Navigation controls: +, -, Fit
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    val center = Offset(screenWidth / 2f, 250f * density)
                    onViewportChange(zoomAround(currentViewport, center, 1.25f))
                },
                modifier = Modifier.padding(bottom = 8.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            FloatingActionButton(
                onClick = {
                    val center = Offset(screenWidth / 2f, 250f * density)
                    onViewportChange(zoomAround(currentViewport, center, 0.8f))
                },
                modifier = Modifier.padding(bottom = 8.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            FloatingActionButton(
                onClick = {
                    if (screenWidth > 0f) {
                        val fit = clampScale(fitScale(screenWidth, density))
                        onViewportChange(ViewportState(scale = fit, translation = Offset.Zero))
                    }
                },
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = "Fit to screen")
            }
        }
    }
}
