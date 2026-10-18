package com.example.fires.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.fires.util.LocationChecks
import com.example.fires.util.LocationGate
import com.example.fires.util.resolveLocationGate

/**
 * Everything a screen needs to deal with location: the current [gate] (permission + GPS switch)
 * and the actions to fix whatever is wrong.
 *
 * DECISION (C8 item 6): location is checked AGAIN when the report form opens (Phase D), not only
 * on the permission screen. Reasons: the person may have skipped it here; permission can be
 * removed later in Settings; and the GPS switch is easy to turn off. Use it like this:
 *
 *     val location = rememberLocationAccess()
 *     if (location.gate == LocationGate.Ready) { /* read GPS */ }
 *     else { /* small banner with location.requestPermission / turnOnGps ... */ }
 *
 * Reporting is NEVER blocked by it. When the gate is not Ready, the form falls back to the manual
 * map pin (locationSource = "pin"). In a fire, sending the report matters more than GPS.
 */
class LocationAccess(
    val gate: LocationGate,
    val requestPermission: () -> Unit,
    val turnOnGps: () -> Unit,
    val openAppSettings: () -> Unit,
    val openLocationSettings: () -> Unit
)

/** Reads permission and GPS state, and re-reads it every time the screen comes back into view. */
@Composable
fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Bumped whenever something may have changed outside our control (coming back from
    // Settings, answering a dialog). Reading it makes the checks below run again.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    // Survive rotation, otherwise the "denied" prompt would vanish when the phone is turned.
    var wasDenied by rememberSaveable { mutableStateOf(false) }
    var canAskAgain by rememberSaveable { mutableStateOf(true) }

    val hasPermission = remember(refresh) { LocationChecks.hasLocationPermission(context) }
    val gpsEnabled = remember(refresh) { LocationChecks.isLocationEnabled(context) }
    val gate = resolveLocationGate(hasPermission, gpsEnabled, wasDenied, canAskAgain)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.none { it }) {
            wasDenied = true
            // After a denial Android says "rationale = true" while it is still willing to ask
            // again. Once that turns false (denied twice), only Settings can grant it.
            canAskAgain = activity != null && LocationChecks.PERMISSIONS.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
        }
        refresh++
    }

    // Result of Google's "Turn on device location?" dialog. Yes or no, just look again.
    val gpsDialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { refresh++ }

    return LocationAccess(
        gate = gate,
        requestPermission = { permissionLauncher.launch(LocationChecks.PERMISSIONS) },
        turnOnGps = {
            LocationChecks.promptEnableLocation(
                context = context,
                launcher = gpsDialogLauncher,
                onAlreadyOn = { refresh++ },
                onFallback = { LocationChecks.openLocationSettings(context) }
            )
        },
        openAppSettings = { LocationChecks.openAppSettings(context) },
        openLocationSettings = { LocationChecks.openLocationSettings(context) }
    )
}

/** The screen's Context may be wrapped (themes etc.). Unwrap until we find the Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
