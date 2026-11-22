package com.mindspark.compose.features.whiteboard.logic

import androidx.compose.ui.graphics.Color
import com.mindspark.compose.ui.theme.Ink

data class PaletteColor(val name: String, val color: Color, val hex: String)

val palette: List<PaletteColor> = listOf(
    PaletteColor("Ink", Color(0xFF273B3B), "#273b3b"),
    PaletteColor("Coral", Color(0xFFED8D74), "#ed8d74"),
    PaletteColor("Butter", Color(0xFFF3C96B), "#f3c96b"),
    PaletteColor("Sage", Color(0xFF9FC9A9), "#9fc9a9"),
    PaletteColor("Sky", Color(0xFF9DC7D8), "#9dc7d8"),
    PaletteColor("Lilac", Color(0xFFB9ADD5), "#b9add5"),
)

fun colorFromHex(hex: String): Color {
    return try {
        val clean = hex.trimStart('#')
        require(clean.length == 6 || clean.length == 8)
        val argb = java.lang.Long.parseLong(
            if (clean.length == 6) "ff$clean" else clean,
            16
        ).toInt()
        Color(argb)
    } catch (e: Exception) {
        Ink
    }
}
