package com.example.fires

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import com.example.fires.ui.navigation.AppNavHost
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.ThemeSettings
import com.example.fires.util.AlertIntents
import com.example.fires.util.CitizenScreen
import com.example.fires.util.PendingCitizenTarget
import com.example.fires.util.PendingAlertTarget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeSettings.load(this)
        enableEdgeToEdge()

        // Only on a fresh launch. When Android recreates the screen (rotation, or the app coming
        // back after its process was killed) it hands back the ORIGINAL launch intent, and the
        // old notification target in it must not fire a second time.
        if (savedInstanceState == null) readAlertTarget(intent)

        setContent {
            val dark = ThemeSettings.isDark()
            // System bar icons follow the app's light / dark choice, not only the phone's setting.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.argb(0xe6, 0xff, 0xff, 0xff),
                        android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                    ) { dark }
                )
                onDispose { }
            }
            FIRESTheme(darkTheme = dark) {
                AppNavHost()
            }
        }
    }

    /**
     * The app was already open (singleTop in the manifest) and a notification was tapped: the
     * intent arrives here instead of opening a second copy.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAlertTarget(intent)
    }

    /**
     * Moves the incident id from the intent into [PendingAlertTarget]. It does NOT navigate:
     * the app may still be at Splash. AppNavHost opens it once the responder area is showing.
     */
    private fun readAlertTarget(intent: Intent?) {
        if (intent == null) return

        // Reopened from the recents screen: Android replays the original intent, old extra
        // included. That is not a new tap.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return

        val incidentId = intent.getStringExtra(AlertIntents.EXTRA_INCIDENT_ID) ?: return
        val citizenScreen = CitizenScreen.fromValue(intent.getStringExtra(AlertIntents.EXTRA_CITIZEN_SCREEN))
        intent.removeExtra(AlertIntents.EXTRA_INCIDENT_ID) // used once
        intent.removeExtra(AlertIntents.EXTRA_CITIZEN_SCREEN)
        // A citizen notification names a screen. Without one it is a responder alert.
        if (citizenScreen != null) {
            PendingCitizenTarget.set(incidentId, citizenScreen)
        } else {
            PendingAlertTarget.set(incidentId)
        }
    }
}
