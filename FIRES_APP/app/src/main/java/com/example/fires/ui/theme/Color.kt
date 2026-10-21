package com.example.fires.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Redesign v3 palette: Ember red, warm neutrals, light and dark.
 *
 * The named colours below (FireRed, Ink, Border...) are used all over the app, in places that
 * are not @Composable. They are properties that read [Palette.dark], which is Compose state set
 * by [FIRESTheme], so every screen recolours when the mode changes and no call site had to change.
 */
object Palette {
    var dark by mutableStateOf(false)
}

private fun pick(light: Long, dark: Long) = Color(if (Palette.dark) dark else light)

// Fixed colour of buttons and the SOS disc: white text on it passes contrast in both modes.
val Ember = Color(0xFFD3281C)
val EmberPressed = Color(0xFFB5220F)

val FireRed: Color get() = pick(0xFFD3281C, 0xFFF0645A)       // as text or an icon on the surface
val FireRedDark: Color get() = pick(0xFF8F1D14, 0xFFF8B4AD)
val FireRedSoft: Color get() = pick(0xFFFDE8E5, 0xFF3A1F1C)
val Ink: Color get() = pick(0xFF1B1A19, 0xFFF5F1EE)
val Gray600: Color get() = pick(0xFF6B6561, 0xFFA8A09B)
val Gray300: Color get() = pick(0xFFB9B0AB, 0xFF5A524E)
val Canvas: Color get() = pick(0xFFFAF7F5, 0xFF14110F)
val Border: Color get() = pick(0xFFEFE8E5, 0xFF322D2A)
val Amber: Color get() = pick(0xFFB45309, 0xFFF2B84B)
val Green: Color get() = pick(0xFF16803D, 0xFF5FD08A)
val Blue: Color get() = pick(0xFF1A5FD0, 0xFF8AB4F8)
val White = Color(0xFFFFFFFF)
