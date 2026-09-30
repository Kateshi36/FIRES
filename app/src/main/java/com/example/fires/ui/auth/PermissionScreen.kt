package com.example.fires.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.LocationChecks
import com.example.fires.util.LocationGate
import com.example.fires.util.resolveLocationGate

/**
 * Permission screen (C8). Shown once to people who are not logged in, before the login screen.
 *
 * 1. Explains why the app wants location, then asks for it.
 * 2. If the person says no: a prompt with "Try again" and "Open settings".
 * 3. If location permission is fine but the phone's GPS switch is off: a prompt to turn it on.
 *
 * As soon as permission is granted AND GPS is on, it moves on by itself. "Skip for now" is also
 * offered, so nobody is stuck here: responders do not need GPS to log in, and a citizen can
 * still mark the fire on the map by hand.
 *
 * There is no ViewModel: everything shown comes from Android itself (permission state, GPS
 * switch), which is re-read whenever the screen comes back into view.
 *
 * @param onContinue called once, when the person is ready or taps Skip.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // The effect below outlives the first composition, so it must call the newest lambda.
    val currentOnContinue by rememberUpdatedState(onContinue)

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

    LaunchedEffect(gate) {
        if (gate == LocationGate.Ready) currentOnContinue()
    }

    PermissionContent(
        gate = gate,
        onAllow = { permissionLauncher.launch(LocationChecks.PERMISSIONS) },
        onTryAgain = { permissionLauncher.launch(LocationChecks.PERMISSIONS) },
        onOpenAppSettings = { LocationChecks.openAppSettings(context) },
        onTurnOnGps = {
            LocationChecks.promptEnableLocation(
                context = context,
                launcher = gpsDialogLauncher,
                onAlreadyOn = { refresh++ },
                onFallback = { LocationChecks.openLocationSettings(context) }
            )
        },
        onOpenLocationSettings = { LocationChecks.openLocationSettings(context) },
        onSkip = { currentOnContinue() }
    )
}

/**
 * The look of the permission screen. No Android calls here, so it can be previewed.
 * Same layout as the login screen: centred when it fits, scrolls when it does not.
 */
@Composable
fun PermissionContent(
    gate: LocationGate,
    onAllow: () -> Unit,
    onTryAgain: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTurnOnGps: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        // Ready means we are already leaving this screen, so draw nothing (avoids a flash).
        if (gate == LocationGate.Ready) return@Surface

        val icon: ImageVector
        val title: String
        val body: String
        when (gate) {
            LocationGate.AskPermission -> {
                icon = Icons.Filled.LocationOn
                title = "Allow location access"
                body = "F.I.R.E.S. uses your location to show responders exactly where a fire " +
                    "is, so help can reach you faster."
            }
            is LocationGate.PermissionDenied -> {
                icon = Icons.Filled.LocationOff
                title = "Location permission denied"
                body = "Without it, the app cannot find you automatically. You can still mark " +
                    "the fire on the map by hand, but GPS is faster and more accurate in an emergency."
            }
            LocationGate.GpsOff -> {
                icon = Icons.Filled.GpsOff
                title = "Turn on your location (GPS)"
                body = "Location access is allowed, but your phone's location switch is off. " +
                    "Turn it on so your reports carry your exact position."
            }
            LocationGate.Ready -> return@Surface // handled above, needed to make `when` exhaustive
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconBadge(icon)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // The two short reasons only on the first ask. Later screens are about fixing.
                    if (gate == LocationGate.AskPermission) {
                        Spacer(Modifier.height(16.dp))
                        ReasonRow("Pins your report at the exact spot of the fire")
                        Spacer(Modifier.height(8.dp))
                        ReasonRow("Helps B-FLARE and BDRRMO find you quickly")
                    }

                    // Android stops showing the dialog after two denials, so say what to do instead.
                    if (gate is LocationGate.PermissionDenied && !gate.canAskAgain) {
                        Spacer(Modifier.height(16.dp))
                        ErrorBanner(
                            "Android won't show the request again. Tap Open settings, then " +
                                "Permissions, then Location, and choose Allow."
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    when (gate) {
                        LocationGate.AskPermission ->
                            PrimaryButton(text = "Allow location", onClick = onAllow)

                        is LocationGate.PermissionDenied -> {
                            if (gate.canAskAgain) {
                                // "Try again" only when Android will actually show the dialog.
                                PrimaryButton(text = "Try again", onClick = onTryAgain)
                                Spacer(Modifier.height(12.dp))
                                SecondaryButton(text = "Open settings", onClick = onOpenAppSettings)
                            } else {
                                PrimaryButton(text = "Open settings", onClick = onOpenAppSettings)
                            }
                        }

                        LocationGate.GpsOff -> {
                            PrimaryButton(text = "Turn on GPS", onClick = onTurnOnGps)
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton(text = "Open location settings", onClick = onOpenLocationSettings)
                        }

                        LocationGate.Ready -> Unit
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip for now", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Big round icon at the top of the screen. */
@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // decorative, the title says the same thing
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
    }
}

@Composable
private fun ReasonRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The screen's Context may be wrapped (themes etc.). Unwrap until we find the Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------- Previews ----------

@Composable
private fun PermissionPreviewBody(gate: LocationGate) {
    FIRESTheme {
        PermissionContent(
            gate = gate,
            onAllow = {}, onTryAgain = {}, onOpenAppSettings = {},
            onTurnOnGps = {}, onOpenLocationSettings = {}, onSkip = {}
        )
    }
}

@Preview(name = "Permission - ask", showSystemUi = true)
@Composable
private fun PermissionAskPreview() {
    PermissionPreviewBody(LocationGate.AskPermission)
}

@Preview(name = "Permission - denied (can ask again)", showSystemUi = true)
@Composable
private fun PermissionDeniedPreview() {
    PermissionPreviewBody(LocationGate.PermissionDenied(canAskAgain = true))
}

@Preview(name = "Permission - denied for good", showSystemUi = true)
@Composable
private fun PermissionDeniedForGoodPreview() {
    PermissionPreviewBody(LocationGate.PermissionDenied(canAskAgain = false))
}

@Preview(name = "Permission - GPS off", showSystemUi = true)
@Composable
private fun PermissionGpsOffPreview() {
    PermissionPreviewBody(LocationGate.GpsOff)
}
