package com.zara.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF9B59B6)
private val DarkBackground = Color(0xFF0D0D0D)
private val DarkSurface = Color(0xFF1A1A1A)

private val ZaraDarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF252525),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFBBBBBB)
)

private val ZaraLightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onSurface = Color(0xFF0D0D0D)
)

@Composable
fun ZaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ZaraDarkColors else ZaraLightColors,
        content = content
    )
}
