package com.vlad.gallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFE0E0E0)
val FavoriteRed = Color(0xFFFF5252)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFB8B8B8),
    onSecondary = Color(0xFF111111),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0C0C0F),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1A1A1F),
    onSurfaceVariant = Color(0xFFB0B0B6),
    outline = Color(0xFF3A3A40),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF17171A),
    onPrimary = Color(0xFFFAFAFA),
    secondary = Color(0xFF505055),
    onSecondary = Color(0xFFFAFAFA),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF17171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFF5A5A60),
    outline = Color(0xFFC4C4CA),
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
