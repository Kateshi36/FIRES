package com.example.fires.ui.responder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.Assignment
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.Verification
import com.example.fires.data.model.fireSizeEnum
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.data.model.verificationEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.ChipGrid
import com.example.fires.ui.common.ChipPill
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LocationAccess
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.MapPoint
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.rememberLocationAccess
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.common.severityColor
import com.example.fires.ui.common.statusColor
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.FireRed
import com.example.fires.ui.theme.Gray600
import com.example.fires.ui.theme.Green
import com.example.fires.util.AssignmentRules
import com.example.fires.util.BatteryOptimization
import com.example.fires.util.BatteryPromptRules
import com.example.fires.util.IncidentActionRules
import com.example.fires.util.ResolveRules
import com.example.fires.util.IncidentDetailRules
import com.example.fires.util.LocationGate
import com.example.fires.util.LocationShareRules
import com.example.fires.util.MapsIntents
import com.example.fires.util.RouteRules
import com.example.fires.data.model.RouteStep
import com.example.fires.service.LiveRoute
import com.example.fires.service.LocationShareService
import com.example.fires.util.dateTimeLabel
import com.example.fires.viewmodel.IncidentDetailUiState
import com.example.fires.viewmodel.IncidentDetailViewModel
import com.example.fires.viewmodel.PhotoState
import com.google.firebase.Timestamp

/**
 * Incident detail (E2) with the responder's actions (E3, E4): everything a responder needs to judge
 * one report, then verify / false / duplicate, severity, status and assignments, and resolving it
 * with remarks (E5).
 *
 * @param onOpenChat opens the chat thread for this report.
 * @param onAssign opens the assign screen for this report.
 * @param onResolve opens the resolve screen (remarks) for this report.
 * @param onOpenIncident opens another report, used for the merged duplicates and the main report.
 */
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onAssign: () -> Unit,
    onResolve: () -> Unit,
    onOpenIncident: (String) -> Unit,
    viewModel: IncidentDetailViewModel = viewModel(factory = IncidentDetailViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // H2: permission and GPS state for "Start response". Re-read every time the screen resumes.
    val location = rememberLocationAccess()

    // H5e: the first "Start response" asks about battery optimization before the service starts.
    // rememberSaveable: turning the phone must not lose the question. Whatever the answer, the
    // service starts, so the question can never stop a real response.
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            title = BatteryPromptRules.LOCATION_TITLE,
            message = BatteryPromptRules.LOCATION_MESSAGE,
            onAllow = {
                showBatteryDialog = false
                // Start FIRST: opening the system dialog sends this app to the background, and
                // Android 12+ only lets a foreground service start while the app is visible.
                LocationShareService.start(context, incidentId)
                if (!BatteryOptimization.requestAllow(context)) {
                    Toast.makeText(context, "Could not open the battery settings.", Toast.LENGTH_LONG).show()
                }
            },
            onNotNow = {
                showBatteryDialog = false
                LocationShareService.start(context, incidentId)
            }
        )
    }

    IncidentDetailContent(
        state = state,
        locationAccess = location,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onOpenIncident = onOpenIncident,
        onRetry = viewModel::retry,
        onRetryPhoto = viewModel::retryPhoto,
        actions = IncidentDetailActions(
            onReview = viewModel::review,
            onSetSeverity = viewModel::setSeverity,
            onAdvanceStatus = viewModel::advanceStatus,
            onStepBack = viewModel::stepBackStatus,
            onAssign = onAssign,
            onResolve = onResolve,
            onDismissError = viewModel::dismissActionError,
            // H2: the service writes the position; the ViewModel reads its state to flip the button.
            onStartResponse = {
                val ask = BatteryPromptRules.shouldAskAtFirstStartResponse(
                    isIgnoringOptimizations = BatteryOptimization.isIgnoring(context),
                    alreadyAskedForResponse = BatteryOptimization.wasAskedForResponse(context)
                )
                if (ask) {
                    BatteryOptimization.markAskedForResponse(context) // asked once, even if the app dies now
                    showBatteryDialog = true
                } else {
                    LocationShareService.start(context, incidentId)
                }
            },
            onStopResponse = { LocationShareService.stop(context) },
            // H3: hands the scene to Google Maps for voice turn-by-turn navigation.
            onOpenMaps = {
                state.incident?.let { MapsIntents.openNavigation(context, it.latitude, it.longitude) }
            }
        ),
        // The dialer only opens with the number filled in. The responder still presses Call, so
        // nobody is dialled by accident and the app needs no CALL_PHONE permission.
        onCall = { dialUri ->
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(dialUri)))
            } catch (_: ActivityNotFoundException) {
                // A device with no phone app (a tablet). The number is still on screen.
            }
        }
    )
}

/** What the responder can do on this screen. All no-ops by default, so previews need none of it. */
data class IncidentDetailActions(
    val onReview: (Verification) -> Unit = {},
    val onSetSeverity: (Severity) -> Unit = {},
    val onAdvanceStatus: () -> Unit = {},
    val onStepBack: () -> Unit = {},
    val onAssign: () -> Unit = {},
    val onResolve: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onStartResponse: () -> Unit = {},
    val onStopResponse: () -> Unit = {},
    val onOpenMaps: () -> Unit = {}
)

/** The look of the detail screen. No ViewModel here, so it can be previewed. */
@Composable
fun IncidentDetailContent(
    state: IncidentDetailUiState,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenIncident: (String) -> Unit,
    onRetry: () -> Unit,
    onRetryPhoto: () -> Unit,
    onCall: (String) -> Unit,
    actions: IncidentDetailActions = IncidentDetailActions(),
    /** Null in previews: the live-response panel then treats location as ready. */
    locationAccess: LocationAccess? = null
) {
    val incident = state.incident

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Incident detail", style = MaterialTheme.typography.headlineSmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    incident == null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner("We couldn't find this incident. It may have been removed.")
                    }

                    else -> IncidentDetails(
                        state = state,
                        incident = incident,
                        onOpenIncident = onOpenIncident,
                        onRetryPhoto = onRetryPhoto,
                        onCall = onCall,
                        actions = actions,
                        locationAccess = locationAccess
                    )
                }
            }

            if (incident != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    PrimaryButton(text = "Open chat", onClick = onOpenChat)
                }
            }
        }
    }
}

@Composable
private fun IncidentDetails(
    state: IncidentDetailUiState,
    incident: Incident,
    onOpenIncident: (String) -> Unit,
    onRetryPhoto: () -> Unit,
    onCall: (String) -> Unit,
    actions: IncidentDetailActions,
    locationAccess: LocationAccess?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Headline: what, how bad, where it stands ----
        Column {
            Text(incident.fireTypeEnum().label, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityChip(incident.severityEnum())
                StatusChip(incident.statusEnum())
                val verification = incident.verificationEnum()
                ChipPill(verification.label, verificationColor(verification))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Severity: " + IncidentDetailRules.severitySourceLabel(incident.severitySource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (incident.isSuspicious) FlaggedNotice()

        if (incident.isDuplicate) {
            DuplicateOfPanel(
                primary = state.primary,
                primaryId = incident.duplicateOf,
                onOpenIncident = onOpenIncident
            )
        }

        // ---- What the responder can do (E3, E4) ----
        ActionsPanel(incident = incident, actionError = state.actionError, actions = actions)
        LiveResponsePanel(state = state, incident = incident, location = locationAccess, actions = actions)
        AssignedPanel(state.assignments)

        // ---- What the reporter said ----
        Panel(title = "Description") {
            if (incident.description.isBlank()) {
                Text(
                    "No description was added.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(incident.description, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (incident.hasPhoto) {
            Panel(title = "Photo") { PhotoBox(state.photo, onRetryPhoto) }
        }

        Panel(title = "Situation") {
            DetailRow("Fire size", incident.fireSizeEnum().label)
            DetailRow("People at risk", IncidentDetailRules.peopleAtRiskLabel(incident.peopleAtRisk))
            DetailRow(
                "Trapped",
                if (incident.trapped) "Yes, people may be trapped" else "No",
                valueColor = if (incident.trapped) FireRed else null,
                bold = incident.trapped
            )
            DetailRow("Vulnerable", IncidentDetailRules.vulnerableLabel(incident.vulnerablePersons))
            DetailRow("Hazards nearby", IncidentDetailRules.hazardsLabel(incident.hazards))
        }

        // ---- Where ----
        Panel(title = "Location") {
            DetailRow("Address", incident.addressText.ifBlank { "No address text" })
            DetailRow(
                "Coordinates",
                IncidentDetailRules.coordinatesLabel(incident.latitude, incident.longitude)
            )
            DetailRow("Pinned by", IncidentDetailRules.locationSourceLabel(incident.locationSource))
            Spacer(Modifier.height(8.dp))
            FiresMap(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                center = LatLon(incident.latitude, incident.longitude),
                zoom = 17.0,
                // H3: while sharing, the blue dot is the responder and the line is the road route.
                userLocation = state.responderPosition,
                route = state.liveRoute?.route?.points.orEmpty(),
                // H5d: a straight-line estimate is dashed, a road route is solid.
                routeDashed = state.liveRoute?.route?.isEstimate == true,
                fitRouteKey = if (state.liveRoute != null) incident.id else null,
                markers = listOf(
                    MapPoint(
                        id = incident.id,
                        latitude = incident.latitude,
                        longitude = incident.longitude,
                        title = incident.fireTypeEnum().label,
                        // Same colors as the dashboard: fill is severity, ring is status.
                        color = severityColor(incident.severityEnum()),
                        ringColor = statusColor(incident.statusEnum()),
                        highlighted = incident.isSuspicious
                    )
                )
            )
        }

        // ---- Who ----
        Panel(title = "Reporter") {
            DetailRow("Name", incident.reporterName.ifBlank { "Unknown" })
            DetailRow("Contact", IncidentDetailRules.contactLabel(incident.reporterContact))
            val dial = IncidentDetailRules.dialUri(incident.reporterContact)
            if (dial != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Call reporter", onClick = { onCall(dial) })
            }
            Spacer(Modifier.height(4.dp))
            DetailRow("Reported", incident.sentLabel())
            incident.updatedAt?.toDate()?.time?.let { DetailRow("Last updated", dateTimeLabel(it)) }
        }

        // ---- Merged duplicates ----
        if (state.duplicates.isNotEmpty()) {
            Panel(title = "Merged duplicate reports (${state.duplicates.size})") {
                Text(
                    "Other people reported this same fire. Each one is listed here with its own reporter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.duplicates.forEach { duplicate ->
                        DuplicateRow(duplicate, onClick = { onOpenIncident(duplicate.id) })
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ---------- Actions (E3, E4) ----------

/**
 * Verify / false / duplicate, status, assign and severity. Only the actions that make sense for
 * the report's current status are drawn (see IncidentActionRules). False and duplicate close the
 * report, so they ask first.
 */
@Composable
private fun ActionsPanel(incident: Incident, actionError: String?, actions: IncidentDetailActions) {
    val status = incident.statusEnum()
    var confirm by rememberSaveable { mutableStateOf<Verification?>(null) }

    Panel(title = "Actions") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (actionError != null) {
                ErrorBanner(actionError)
                TextButton(onClick = actions.onDismissError) { Text("Dismiss") }
            }

            if (!status.isActive) {
                Text(
                    IncidentActionRules.CLOSED_NOTE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (IncidentActionRules.canReview(status)) {
                    Text("Is this report real?", style = MaterialTheme.typography.labelLarge)
                    PrimaryButton(text = "Verify report", onClick = { actions.onReview(Verification.VERIFIED) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "False report",
                            onClick = { confirm = Verification.FALSE },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Duplicate",
                            onClick = { confirm = Verification.DUPLICATE },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val next = IncidentActionRules.nextStatus(status)
                if (next != null) {
                    PrimaryButton(
                        text = "Mark as " + next.label.lowercase(),
                        onClick = actions.onAdvanceStatus
                    )
                }
                // The last step has no plain button: resolving needs remarks, so it opens its own screen.
                if (ResolveRules.canResolve(status)) {
                    PrimaryButton(text = "Resolve incident", onClick = actions.onResolve)
                }
                val previous = IncidentActionRules.previousStatus(status)
                if (previous != null) {
                    TextButton(onClick = actions.onStepBack) {
                        Text("Undo: back to " + previous.label.lowercase())
                    }
                }

                if (IncidentActionRules.canAssign(status)) {
                    SecondaryButton(text = "Assign responders / resources", onClick = actions.onAssign)
                }

                if (IncidentActionRules.canOverrideSeverity(status)) {
                    Text("Change severity", style = MaterialTheme.typography.labelLarge)
                    ChipGrid(
                        items = Severity.entries.toList(),
                        isSelected = { it == incident.severityEnum() },
                        label = { it.label },
                        onToggle = actions.onSetSeverity
                    )
                }
            }
        }
    }

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(IncidentActionRules.confirmTitle(pending)) },
            text = { Text(IncidentActionRules.confirmBody(pending)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    actions.onReview(pending)
                }) { Text(IncidentActionRules.confirmButton(pending)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

// ---------- Live response (H2) ----------

/**
 * "Start response" shares this responder's live position until they reach the scene (or tap Stop).
 * Only drawn for a responder assigned to a DISPATCHED incident. When the phone's location is not
 * ready the button waits, and a notice with one button fixes it. Anyone else on a DISPATCHED
 * incident sees a short note saying why there is no button.
 */
@Composable
private fun LiveResponsePanel(
    state: IncidentDetailUiState,
    incident: Incident,
    location: LocationAccess?,
    actions: IncidentDetailActions
) {
    if (!state.canShareLocation && !state.isSharingLocation) {
        if (incident.statusEnum() == IncidentStatus.DISPATCHED) {
            Panel(title = "Live response") {
                Text(
                    LocationShareRules.NOT_ASSIGNED_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Panel(title = "Live response") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isSharingLocation) {
                Text("Sharing your location", style = MaterialTheme.typography.titleMedium, color = Green)
                // H5b: the phone's GPS went off mid-response. Offer the one button that fixes it.
                if (state.gpsLost) {
                    NoticeRow(
                        message = LocationShareRules.GPS_OFF_NOTE,
                        buttonLabel = "Turn on GPS",
                        onButton = { location?.turnOnGps?.invoke() }
                    )
                }
                RouteBar(state.liveRoute)
                SecondaryButton(text = "Open in Google Maps", onClick = actions.onOpenMaps)
                DirectionsList(state.liveRoute?.route?.steps.orEmpty())
                Text(
                    LocationShareRules.SHARING_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryButton(text = "Stop sharing", onClick = actions.onStopResponse)
            } else {
                Text(
                    LocationShareRules.SHARING_NOTE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val gate = location?.gate ?: LocationGate.Ready
                if (location != null && gate != LocationGate.Ready) {
                    LocationNeededNotice(gate, location)
                }
                PrimaryButton(
                    text = "Start response",
                    onClick = actions.onStartResponse,
                    enabled = gate == LocationGate.Ready
                )
                // Works without sharing: Google Maps finds its own route from where the phone is.
                SecondaryButton(text = "Open in Google Maps", onClick = actions.onOpenMaps)
            }
        }
    }
}

/** The bar above the route: "2.4 km, about 6 min", or a short wait message before the first route. */
@Composable
private fun RouteBar(live: LiveRoute?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = live?.let { RouteRules.routeSummary(it.route) } ?: RouteRules.FINDING_ROUTE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/** The turn-by-turn steps from the routing service, folded away until asked for. */
@Composable
private fun DirectionsList(steps: List<RouteStep>) {
    if (steps.isEmpty()) return
    var show by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { show = !show }) {
        Text(if (show) "Hide directions" else "Show directions (${steps.size} steps)")
    }
    if (show) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { index, step ->
                Row {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
                        if (step.distanceMeters > 0) {
                            Text(
                                RouteRules.distanceLabel(step.distanceMeters),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Why "Start response" is waiting, with the one button that fixes it. */
@Composable
private fun LocationNeededNotice(gate: LocationGate, location: LocationAccess) {
    val message: String
    val buttonLabel: String
    val onButton: () -> Unit
    when (gate) {
        LocationGate.Ready -> return
        LocationGate.AskPermission -> {
            message = "Allow location so staff and the reporter can see where you are."
            buttonLabel = "Allow"
            onButton = location.requestPermission
        }
        is LocationGate.PermissionDenied -> {
            message = "Location is off for this app, so your position can't be shared."
            buttonLabel = if (gate.canAskAgain) "Allow" else "Settings"
            onButton = if (gate.canAskAgain) location.requestPermission else location.openAppSettings
        }
        LocationGate.GpsOff -> {
            message = "Your phone's GPS is off. Turn it on to share your position."
            buttonLabel = "Turn on"
            onButton = location.turnOnGps
        }
    }
    NoticeRow(message = message, buttonLabel = buttonLabel, onButton = onButton)
}

/** A short message with one button on its right. Used for both "Start is waiting" and "GPS went off". */
@Composable
private fun NoticeRow(message: String, buttonLabel: String, onButton: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onButton) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun AssignedPanel(assignments: List<Assignment>) {
    Panel(title = if (assignments.isEmpty()) "Assigned" else "Assigned (${assignments.size})") {
        if (assignments.isEmpty()) {
            Text(
                "No one has been assigned yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                assignments.forEach { AssignmentRow(it) }
            }
        }
    }
}

@Composable
private fun AssignmentRow(assignment: Assignment) {
    Column {
        Text(assignment.unit, style = MaterialTheme.typography.titleSmall)
        Text(
            AssignmentRules.resourcesLabel(assignment.assignedResources),
            style = MaterialTheme.typography.bodyLarge
        )
        val time = assignment.assignedAt?.toDate()?.time?.let(::dateTimeLabel) ?: "Just now"
        Text(
            "Assigned by " + assignment.responderName.ifBlank { "a responder" } + "  ·  " + time,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Pieces ----------

@Composable
private fun FlaggedNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Amber.copy(alpha = 0.16f),
        border = BorderStroke(2.dp, Amber)
    ) {
        Text(
            text = IncidentDetailRules.FLAGGED_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/** Shown on a report that was grouped under another one: says which, and lets you jump to it. */
@Composable
private fun DuplicateOfPanel(primary: Incident?, primaryId: String?, onOpenIncident: (String) -> Unit) {
    Panel(title = "Duplicate report") {
        Text(
            "This report was grouped under another report of the same fire.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (primary != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${primary.fireTypeEnum().label}, " +
                    primary.addressText.ifBlank { "no address" } + ", " + primary.sentLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!primaryId.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Open main report", onClick = { onOpenIncident(primaryId) })
        }
    }
}

@Composable
private fun PhotoBox(photo: PhotoState, onRetry: () -> Unit) {
    when (photo) {
        // None only shows for a moment while the report is still loading in.
        PhotoState.None, PhotoState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is PhotoState.Loaded -> Image(
            bitmap = photo.bitmap.asImageBitmap(),
            contentDescription = "Photo of the fire sent by the reporter",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )

        PhotoState.Unavailable -> Column {
            Text(
                "The photo isn't available yet. It is sent after the report, so it may still be on its way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Try again", onClick = onRetry)
        }
    }
}

@Composable
private fun DuplicateRow(duplicate: Incident, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open this duplicate report", onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (duplicate.isSuspicious) 2.dp else 1.dp, if (duplicate.isSuspicious) Amber else Border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = duplicate.reporterName.ifBlank { "Unknown reporter" } + "  ·  " + duplicate.sentLabel(),
                style = MaterialTheme.typography.labelLarge
            )
            if (duplicate.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    duplicate.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Contact: " + IncidentDetailRules.contactLabel(duplicate.reporterContact),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (duplicate.isSuspicious) {
                Spacer(Modifier.height(4.dp))
                Text("Flagged for review", style = MaterialTheme.typography.labelLarge, color = Amber)
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun verificationColor(verification: Verification): Color = when (verification) {
    Verification.PENDING -> Amber
    Verification.VERIFIED -> Green
    Verification.FALSE -> FireRed
    Verification.DUPLICATE -> Gray600
}

/** A report with no server time yet was just sent from a phone that is still offline. */
private fun Incident.sentLabel(): String =
    submittedAt?.toDate()?.time?.let(::dateTimeLabel) ?: "Just now"

// ---------- Previews ----------

private fun sample(
    id: String = "demo",
    status: IncidentStatus = IncidentStatus.REPORTED,
    flagged: Boolean = false,
    duplicateOf: String? = null
) = Incident(
    id = id,
    reporterName = "Maria Santos",
    reporterContact = "0917 123 4567",
    fireType = FireType.STRUCTURAL.value,
    description = "Smoke coming from the second floor of the house beside the sari-sari store.",
    latitude = 14.5995,
    longitude = 120.9842,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    peopleAtRisk = 4,
    vulnerablePersons = listOf("children", "elderly"),
    trapped = true,
    hazards = listOf("lpg_tank"),
    hasPhoto = true,
    status = status.value,
    severity = Severity.CRITICAL.value,
    verification = Verification.PENDING.value,
    isSuspicious = flagged,
    isDuplicate = duplicateOf != null,
    duplicateOf = duplicateOf,
    submittedAt = Timestamp(1_790_000_000L, 0)
)

@Composable
private fun PreviewScreen(state: IncidentDetailUiState) {
    FIRESTheme {
        IncidentDetailContent(
            state = state, onBack = {}, onOpenChat = {}, onOpenIncident = {},
            onRetry = {}, onRetryPhoto = {}, onCall = {}
        )
    }
}

@Preview(name = "Detail - flagged with duplicates", showSystemUi = true)
@Composable
private fun DetailFlaggedPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(flagged = true),
        duplicates = listOf(
            sample("d1", duplicateOf = "demo").copy(reporterName = "Jose Reyes", description = "Same fire, flames now visible."),
            sample("d2", duplicateOf = "demo", flagged = true).copy(reporterName = "")
        ),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        assignments = listOf(
            Assignment(unit = "BFP", assignedResources = listOf("Fire truck", "Water tanker"), responderName = "Ana Cruz")
        ),
        isLoading = false
    )
)

@Preview(name = "Detail - verified, with error", showSystemUi = true)
@Composable
private fun DetailVerifiedPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.VERIFIED).copy(verification = Verification.VERIFIED.value),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        actionError = IncidentActionRules.ACTION_ERROR,
        isLoading = false
    )
)

@Preview(name = "Detail - dispatched, can start response", showSystemUi = true)
@Composable
private fun DetailCanStartPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISPATCHED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        canShareLocation = true,
        isLoading = false
    )
)

@Preview(name = "Detail - sharing location", showSystemUi = true)
@Composable
private fun DetailSharingPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISPATCHED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        canShareLocation = true,
        isSharingLocation = true,
        isLoading = false
    )
)

@Preview(name = "Detail - sharing, GPS turned off", showSystemUi = true)
@Composable
private fun DetailGpsOffPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISPATCHED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        canShareLocation = true,
        isSharingLocation = true,
        gpsLost = true,
        isLoading = false
    )
)

@Preview(name = "Detail - sharing, with route", showSystemUi = true)
@Composable
private fun DetailRoutePreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISPATCHED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        canShareLocation = true,
        isSharingLocation = true,
        liveRoute = LiveRoute(
            incidentId = "demo",
            route = com.example.fires.data.model.Route(
                points = listOf(LatLon(14.59, 120.98), LatLon(14.5995, 120.9842)),
                distanceMeters = 2_400,
                durationSeconds = 360,
                steps = listOf(
                    RouteStep("Head out on Rizal St.", 1_200),
                    RouteStep("Turn left onto Mabini St.", 1_200),
                    RouteStep("Arrive at the scene", 0)
                )
            ),
            computedAtMillis = 0L
        ),
        isLoading = false
    )
)

@Preview(name = "Detail - sharing, straight line (no route)", showSystemUi = true)
@Composable
private fun DetailStraightLinePreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISPATCHED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        canShareLocation = true,
        isSharingLocation = true,
        liveRoute = LiveRoute(
            incidentId = "demo",
            route = com.example.fires.util.RouteRules.straightLine(
                LatLon(14.5895, 120.9842), LatLon(14.5995, 120.9842)
            ),
            computedAtMillis = 0L
        ),
        isLoading = false
    )
)

@Preview(name = "Detail - closed", showSystemUi = true)
@Composable
private fun DetailClosedPreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample(status = IncidentStatus.DISMISSED),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        isLoading = false
    )
)

@Preview(name = "Detail - is a duplicate", showSystemUi = true)
@Composable
private fun DetailDuplicatePreview() = PreviewScreen(
    IncidentDetailUiState(
        incident = sample("d1", duplicateOf = "demo"),
        primary = sample(),
        photo = com.example.fires.viewmodel.PhotoState.Unavailable,
        isLoading = false
    )
)

@Preview(name = "Detail - not found", showSystemUi = true)
@Composable
private fun DetailMissingPreview() = PreviewScreen(IncidentDetailUiState(isLoading = false))

@Preview(name = "Detail - load error", showSystemUi = true)
@Composable
private fun DetailErrorPreview() =
    PreviewScreen(IncidentDetailUiState(isLoading = false, error = IncidentDetailViewModel.LOAD_ERROR))
