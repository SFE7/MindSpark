package com.mindspark.compose.features.whiteboard.logic

import androidx.compose.ui.geometry.Offset

data class ViewportState(
    val scale: Float = 0.8f,
    val translation: Offset = Offset.Zero
)

fun clampScale(s: Float): Float = s.coerceIn(0.3f, 4.0f)

fun screenToWorld(screen: Offset, vp: ViewportState, density: Float): Offset {
    val effectiveScale = vp.scale * density
    return Offset(
        x = (screen.x - vp.translation.x) / effectiveScale,
        y = (screen.y - vp.translation.y) / effectiveScale
    )
}

fun fitScale(screenWidthPx: Float, density: Float): Float {
    return screenWidthPx / (1000f * density)
}

fun zoomAround(vp: ViewportState, focusScreen: Offset, factor: Float): ViewportState {
    val newScale = clampScale(vp.scale * factor)
    if (newScale == vp.scale) return vp

    val scaleRatio = newScale / vp.scale
    val newTranslation = Offset(
        x = focusScreen.x - (focusScreen.x - vp.translation.x) * scaleRatio,
        y = focusScreen.y - (focusScreen.y - vp.translation.y) * scaleRatio
    )
    return ViewportState(scale = newScale, translation = newTranslation)
}
