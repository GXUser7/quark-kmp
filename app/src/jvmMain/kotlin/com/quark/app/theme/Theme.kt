package com.quark.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours taken from the current cover, as the Dart build did for its window
 * chrome and gradients. Defaults are neutral so the UI is readable before any
 * artwork has been decoded.
 */
data class AccentColors(
    val primary: Color = Color(0xFF7E8CE0),
    val secondary: Color = Color(0xFF5A6BC4),
    val tertiary: Color = Color(0xFF3F4A8A),
) {
    val gradient: List<Color> get() = listOf(primary, secondary, tertiary)
}

val LocalAccent = staticCompositionLocalOf { AccentColors() }

private val DarkScheme = darkColorScheme(
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF121218),
    surfaceVariant = Color(0xFF1C1C25),
    onSurfaceVariant = Color(0xFF9E9EAE),
)

private val LightScheme = lightColorScheme(
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECECF2),
)

@Composable
fun QuarkTheme(
    dark: Boolean = true,
    accent: AccentColors = AccentColors(),
    content: @Composable () -> Unit,
) {
    val base = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = base.copy(
                primary = accent.primary,
                secondary = accent.secondary,
                tertiary = accent.tertiary,
            ),
            content = content,
        )
    }
}
