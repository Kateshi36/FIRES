package com.example.fires.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = White,
    primaryContainer = Color(0xFFFDE8E5),
    onPrimaryContainer = Color(0xFF8F1D14),
    secondary = Color(0xFF1B1A19),
    onSecondary = White,
    background = Color(0xFFFAF7F5),
    onBackground = Color(0xFF1B1A19),
    surface = White,
    onSurface = Color(0xFF1B1A19),
    surfaceVariant = Color(0xFFEFE8E5),
    onSurfaceVariant = Color(0xFF6B6561),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFFAF7F5),
    surfaceContainer = Color(0xFFF5F0ED),
    surfaceContainerHigh = Color(0xFFEFE8E5),
    surfaceContainerHighest = Color(0xFFE7DFDB),
    outline = Color(0xFFB9B0AB),
    outlineVariant = Color(0xFFEFE8E5),
    error = Color(0xFFB5220F)
)

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = White,
    primaryContainer = Color(0xFF4A2320),
    onPrimaryContainer = Color(0xFFF8B4AD),
    secondary = Color(0xFFF5F1EE),
    onSecondary = Color(0xFF14110F),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFF5F1EE),
    surface = Color(0xFF1F1C1A),
    onSurface = Color(0xFFF5F1EE),
    surfaceVariant = Color(0xFF322D2A),
    onSurfaceVariant = Color(0xFFA8A09B),
    surfaceContainerLowest = Color(0xFF14110F),
    surfaceContainerLow = Color(0xFF1A1715),
    surfaceContainer = Color(0xFF1F1C1A),
    surfaceContainerHigh = Color(0xFF292523),
    surfaceContainerHighest = Color(0xFF322D2A),
    outline = Color(0xFF5A524E),
    outlineVariant = Color(0xFF322D2A),
    error = Color(0xFFF0645A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FIRESTheme(
    darkTheme: Boolean = ThemeSettings.isDark(),
    content: @Composable () -> Unit
) {
    // Set before any child reads the named colours (FireRed, Border...), so there is no light flash.
    if (Palette.dark != darkTheme) Palette.dark = darkTheme
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
