package com.mindspark.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4E2),
    onSecondaryContainer = Ink,
    background = MindSparkBackground,
    onBackground = Ink,
    surface = CardBackground,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE2E7DE),
    onSurfaceVariant = Ink,
    outline = Color(0xFF707973),
)

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    secondary = Color(0xFF8BA5A2),
    onSecondary = Ink,
    background = Color(0xFF192323),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF1E2B2B),
    onSurface = Color(0xFFE1E3DF),
)

@Composable
fun MindSparkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for template backwards compatibility
@Composable
fun MindsparkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MindSparkTheme(darkTheme = darkTheme, content = content)
}
