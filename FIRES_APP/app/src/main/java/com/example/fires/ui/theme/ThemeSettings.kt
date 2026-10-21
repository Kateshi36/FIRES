package com.example.fires.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The person's light / dark choice. Saved on the phone only; with no choice the phone's setting applies. */
object ThemeSettings {
    private const val PREFS = "fires_theme"
    private const val KEY = "mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, newMode.name).apply()
    }

    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

/** One menu row that flips between light and dark. Put it inside a DropdownMenu. */
@Composable
fun ThemeMenuItem(onDone: () -> Unit) {
    val context = LocalContext.current
    val dark = ThemeSettings.isDark()
    DropdownMenuItem(
        text = { Text(if (dark) "Switch to light mode" else "Switch to dark mode") },
        onClick = {
            ThemeSettings.set(context, if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
            onDone()
        }
    )
}
