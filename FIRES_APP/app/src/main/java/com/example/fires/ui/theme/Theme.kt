package com.example.fires.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// The design sheet is light-only, so the app is light-only for now.
private val LightColors = lightColorScheme(
    primary = FireRed,
    onPrimary = White,
    primaryContainer = FireRedSoft,
    onPrimaryContainer = FireRedDark,
    secondary = Ink,
    onSecondary = White,
    background = Canvas,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Border,
    onSurfaceVariant = Gray600,
    outline = Gray300,
    error = FireRedDark
)

@Composable
fun FIRESTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
