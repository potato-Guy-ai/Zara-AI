package com.zara.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF6C63FF)
private val PurpleContainer = Color(0xFFE8E6FF)
private val DarkBackground = Color(0xFF0F0F1A)
private val DarkSurface = Color(0xFF1A1A2E)

private val DarkColorScheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D3780),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF252540),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFBBBBDD)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    background = Color(0xFFF8F8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EFFF),
    onSurface = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFF4A4A6A)
)

@Composable
fun ZaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
