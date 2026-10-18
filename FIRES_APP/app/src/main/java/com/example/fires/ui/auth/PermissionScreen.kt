package com.example.fires.ui.auth

import android.Manifest
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.rememberLocationAccess
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.LocationGate
import com.example.fires.util.NotificationChecks
import com.example.fires.util.PermissionStep
import com.example.fires.util.resolvePermissionStep

/**
 * Permission screen (C8). Shown once to people who are not logged in, before the login screen.
 *
 * Step 1, location: explain why, ask, and handle "denied" and "GPS off" (see [LocationGate]).
 * Step 2, notifications: only on Android 13+ and only if not granted. On older phones this step
 *         never appears, so nothing is shown or asked.
 *
 * Nobody can get stuck here. Each step has a way out that works whatever the answer was:
 * "Continue without location" and "Not now". After the notification prompt the screen moves on
 * whether the person allowed it or not. It also moves on by itself when nothing is left to ask.
 *
 * @param onContinue called once, when there is nothing left to ask.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    // The effect below outlives the first composition, so it must call the newest lambda.
    val currentOnContinue by rememberUpdatedState(onContinue)

    val location = rememberLocationAccess()

    // Saved so rotating the phone does not send the person back to a step they already finished.
    var locationSkipped by rememberSaveable { mutableStateOf(false) }
    var notificationsAnswered by rememberSaveable { mutableStateOf(false) }

    // Android 12 and older: false, so the notifications step is skipped silently.
    val notificationsNeeded = remember { NotificationChecks.needsPrompt(context) }

    // Any answer (Allow or Don't allow) counts as answered: a denial must never block the way.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsAnswered = true }

    val step = resolvePermissionStep(
        locationGate = location.gate,
        locationSkipped = locationSkipped,
        notificationsNeeded = notificationsNeeded,
        notificationsAnswered = notificationsAnswered
    )

    LaunchedEffect(step) {
        if (step == PermissionStep.Done) currentOnContinue()
    }

    PermissionContent(
        step = step,
        actions = PermissionActions(
            onAllowLocation = location.requestPermission,
            onTryAgain = location.requestPermission,
            onOpenAppSettings = location.openAppSettings,
            onTurnOnGps = location.turnOnGps,
            onOpenLocationSettings = location.openLocationSettings,
            onSkipLocation = { locationSkipped = true },
            onAllowNotifications = {
                // Only reachable on Android 13+, where this permission exists.
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onSkipNotifications = { notificationsAnswered = true }
        )
    )
}

/** Every button on the screen. Defaults to "do nothing" so previews stay short. */
data class PermissionActions(
    val onAllowLocation: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onTurnOnGps: () -> Unit = {},
    val onOpenLocationSettings: () -> Unit = {},
    val onSkipLocation: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onSkipNotifications: () -> Unit = {}
)

private data class StepText(val icon: ImageVector, val title: String, val body: String)

/** Title and explanation for a step. Null when there is nothing to show (the screen is leaving). */
private fun stepText(step: PermissionStep): StepText? = when (step) {
    is PermissionStep.Location -> when (step.gate) {
        LocationGate.AskPermission -> StepText(
            icon = Icons.Filled.LocationOn,
            title = "Allow location access",
            body = "F.I.R.E.S. uses your location to show responders exactly where a fire " +
                "is, so help can reach you faster."
        )
        is LocationGate.PermissionDenied -> StepText(
            icon = Icons.Filled.LocationOff,
            title = "Location permission denied",
            body = "Without it, the app cannot find you automatically. You can still mark " +
                "the fire on the map by hand, but GPS is faster and more accurate in an emergency."
        )
        LocationGate.GpsOff -> StepText(
            icon = Icons.Filled.GpsOff,
            title = "Turn on your location (GPS)",
            body = "Location access is allowed, but your phone's location switch is off. " +
                "Turn it on so your reports carry your exact position."
        )
        LocationGate.Ready -> null
    }
    PermissionStep.Notifications -> StepText(
        icon = Icons.Filled.NotificationsActive,
        title = "Get emergency alerts",
        body = "Allow notifications so F.I.R.E.S. can alert you right away, even when the app is closed."
    )
    PermissionStep.Done -> null
}

/**
 * The look of the permission screen. No Android calls here, so it can be previewed.
 * Same layout as the login screen: centred when it fits, scrolls when it does not.
 */
@Composable
fun PermissionContent(
    step: PermissionStep,
    actions: PermissionActions
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        // Nothing to show means we are already leaving this screen. Draw nothing (avoids a flash).
        val text = stepText(step) ?: return@Surface
        val locationGate = (step as? PermissionStep.Location)?.gate

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
                    IconBadge(text.icon)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = text.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = text.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Short reasons only on the two "ask" screens. The fix-it screens need none.
                    if (locationGate == LocationGate.AskPermission) {
                        Spacer(Modifier.height(16.dp))
                        ReasonRow("Pins your report at the exact spot of the fire")
                        Spacer(Modifier.height(8.dp))
                        ReasonRow("Helps B-FLARE and BDRRMO find you quickly")
                    }
                    if (step == PermissionStep.Notifications) {
                        Spacer(Modifier.height(16.dp))
                        ReasonRow("Know when your report is verified")
                        Spacer(Modifier.height(8.dp))
                        ReasonRow("Get replies from responders right away")
                    }

                    // Android stops showing the dialog after two denials, so say what to do instead.
                    if (locationGate is LocationGate.PermissionDenied && !locationGate.canAskAgain) {
                        Spacer(Modifier.height(16.dp))
                        ErrorBanner(
                            "Android won't show the request again. Tap Open settings, then " +
                                "Permissions, then Location, and choose Allow."
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    when (step) {
                        is PermissionStep.Location -> {
                            when (val gate = step.gate) {
                                LocationGate.AskPermission ->
                                    PrimaryButton(text = "Allow location", onClick = actions.onAllowLocation)

                                is LocationGate.PermissionDenied -> {
                                    if (gate.canAskAgain) {
                                        // "Try again" only when Android will actually show the dialog.
                                        PrimaryButton(text = "Try again", onClick = actions.onTryAgain)
                                        Spacer(Modifier.height(12.dp))
                                        SecondaryButton(text = "Open settings", onClick = actions.onOpenAppSettings)
                                    } else {
                                        PrimaryButton(text = "Open settings", onClick = actions.onOpenAppSettings)
                                    }
                                }

                                LocationGate.GpsOff -> {
                                    PrimaryButton(text = "Turn on GPS", onClick = actions.onTurnOnGps)
                                    Spacer(Modifier.height(12.dp))
                                    SecondaryButton(
                                        text = "Open location settings",
                                        onClick = actions.onOpenLocationSettings
                                    )
                                }

                                LocationGate.Ready -> Unit
                            }
                            Spacer(Modifier.height(8.dp))
                            // Always works. Moves on to the next step (notifications) or to login.
                            TextButton(onClick = actions.onSkipLocation) {
                                Text("Continue without location", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        PermissionStep.Notifications -> {
                            PrimaryButton(text = "Allow notifications", onClick = actions.onAllowNotifications)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = actions.onSkipNotifications) {
                                Text("Not now", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        PermissionStep.Done -> Unit
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

// ---------- Previews ----------

@Composable
private fun PermissionPreviewBody(step: PermissionStep) {
    FIRESTheme { PermissionContent(step = step, actions = PermissionActions()) }
}

@Preview(name = "Permission - ask location", showSystemUi = true)
@Composable
private fun PermissionAskPreview() {
    PermissionPreviewBody(PermissionStep.Location(LocationGate.AskPermission))
}

@Preview(name = "Permission - denied (can ask again)", showSystemUi = true)
@Composable
private fun PermissionDeniedPreview() {
    PermissionPreviewBody(PermissionStep.Location(LocationGate.PermissionDenied(canAskAgain = true)))
}

@Preview(name = "Permission - denied for good", showSystemUi = true)
@Composable
private fun PermissionDeniedForGoodPreview() {
    PermissionPreviewBody(PermissionStep.Location(LocationGate.PermissionDenied(canAskAgain = false)))
}

@Preview(name = "Permission - GPS off", showSystemUi = true)
@Composable
private fun PermissionGpsOffPreview() {
    PermissionPreviewBody(PermissionStep.Location(LocationGate.GpsOff))
}

@Preview(name = "Permission - notifications", showSystemUi = true)
@Composable
private fun PermissionNotificationsPreview() {
    PermissionPreviewBody(PermissionStep.Notifications)
}
