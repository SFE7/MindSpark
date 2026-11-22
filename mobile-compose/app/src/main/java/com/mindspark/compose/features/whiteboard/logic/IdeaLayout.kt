package com.mindspark.compose.features.whiteboard.logic

import androidx.compose.ui.geometry.Offset

const val CARD_WIDTH = 210f
const val CARD_HEIGHT = 110f

fun ideaTopLeft(x: Float, y: Float): Offset = Offset(x - CARD_WIDTH / 2f, y - CARD_HEIGHT / 2f)

fun defaultIdeaCentre(index: Int): Offset {
    val col = index % 4
    val row = (index / 4) % 4
    val x = 150f + col * 220f
    val y = 80f + row * 120f
    return Offset(x, y)
}
