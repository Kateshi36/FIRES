package com.example.fires.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.ResponderLocation
import com.example.fires.data.model.Severity
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.MapPoint
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.rememberSmoothPositions
import com.example.fires.ui.common.severityColor
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.common.StatusStepper
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.Gray600
import com.example.fires.ui.theme.Green
import com.example.fires.util.ChatRules
import com.example.fires.util.ResponderTrackingRules
import com.example.fires.util.dateTimeLabel
import com.example.fires.util.statusDetail
import com.example.fires.util.statusHeadline
import com.example.fires.viewmodel.StatusUiState
import com.example.fires.viewmodel.StatusViewModel

/**
 * Report status (D7): follows one report live and shows where it is in the response, using the
 * four-step stepper (Received, Verified, Dispatched, On scene) plus Resolved.
 *
 * @param onBack back to the previous screen.
 * @param onOpenChat opens the chat thread for this report.
 */
@Composable
fun StatusScreen(
    incidentId: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    viewModel: StatusViewModel = viewModel(factory = StatusViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatusContent(state = state, onBack = onBack, onOpenChat = onOpenChat, onRetry = viewModel::retry)
}

/** The look of the status screen. No ViewModel here, so it can be previewed. */
@Composable
fun StatusContent(
    state: StatusUiState,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onRetry: () -> Unit
) {
    val incident = state.incident

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Report status", style = MaterialTheme.typography.headlineSmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    // Missing report: the server refused it, or the link points at nothing.
                    incident == null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(
                            "We couldn't find this report. If you sent it a moment ago it may not " +
                                "have been delivered. If it is urgent, call 911."
                        )
                    }

                    else -> ReportDetails(
                        incident = incident,
                        delivery = state.delivery,
                        responders = state.responders,
                        nowMillis = state.nowMillis
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
private fun ReportDetails(
    incident: Incident,
    delivery: DeliveryState,
    responders: List<ResponderLocation>,
    nowMillis: Long
) {
    val status = incident.statusEnum()
    val banner = ResponderTrackingRules.banner(status, responders, nowMillis)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (delivery == DeliveryState.SENDING) {
            Surface(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(12.dp),
                color = Amber.copy(alpha = 0.16f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Not sent yet. It will go out as soon as you are back online.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // A live region, so TalkBack reads the new headline out when the status changes.
        Text(
            text = statusHeadline(status),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = statusDetail(status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // H4: where the responder is, and when they will arrive.
        if (banner != null) {
            Spacer(Modifier.height(12.dp))
            TrackingBanner(banner)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityChip(incident.severityEnum())
            StatusChip(status)
        }

        if (status == IncidentStatus.DISPATCHED && responders.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TrackingMap(incident = incident, responders = responders, nowMillis = nowMillis)
        }

        Spacer(Modifier.height(24.dp))
        StatusStepper(status)

        Spacer(Modifier.height(24.dp))
        DetailRow("Fire type", incident.fireTypeEnum().label)
        if (incident.addressText.isNotBlank()) DetailRow("Location", incident.addressText)
        DetailRow(
            "Sent",
            incident.submittedAt?.toDate()?.time?.let(::dateTimeLabel) ?: ChatRules.SENDING_LABEL
        )

        if (incident.isDuplicate) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Someone nearby reported this fire too. Your report was added to theirs, so responders have both.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Responder tracking (H4) ----------

/** "Responder is on the way, arriving in about 6 min". A live region, so TalkBack reads each change out. */
@Composable
private fun TrackingBanner(banner: ResponderTrackingRules.Banner) {
    val tint = when (banner.kind) {
        ResponderTrackingRules.Kind.LIVE -> Green
        ResponderTrackingRules.Kind.ARRIVED -> Green
        ResponderTrackingRules.Kind.STALE -> Amber
        ResponderTrackingRules.Kind.WAITING -> Gray600
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint)
    ) {
        Text(
            text = banner.text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(14.dp)
        )
    }
}

/**
 * The fire and the responders on one map. Each responder's dot glides to each new position
 * instead of jumping, and turns grey when its location is more than a minute old. The map zooms
 * out once to fit everyone, then leaves the camera to the citizen.
 *
 * Under the map, a legend names each dot ("Responder 1", "Responder 2 (last seen 2 min ago)")
 * in the same colour, because tapping a marker does not open a label.
 */
@Composable
private fun TrackingMap(incident: Incident, responders: List<ResponderLocation>, nowMillis: Long) {
    val targets = responders.associate { it.id to LatLon(it.latitude, it.longitude) }
    val glided = rememberSmoothPositions(targets)
    val scene = LatLon(incident.latitude, incident.longitude)
    val labels = ResponderTrackingRules.markerLabels(responders, nowMillis)
    val labelById = labels.associateBy { it.responderId }

    FiresMap(
        modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)),
        center = scene,
        zoom = 16.0,
        markers = listOf(
            MapPoint(
                id = incident.id,
                latitude = scene.latitude,
                longitude = scene.longitude,
                title = "Fire location",
                color = severityColor(incident.severityEnum())
            )
        ) + responders.mapNotNull { responder ->
            glided[responder.id]?.let { at ->
                val label = labelById[responder.id]
                MapPoint(
                    id = "responder-" + responder.id,
                    latitude = at.latitude,
                    longitude = at.longitude,
                    title = label?.text ?: "Responder",
                    color = markerColor(label)
                )
            }
        },
        fitRouteKey = incident.id,
        fitPoints = listOf(scene) + targets.values
    )
    Spacer(Modifier.height(8.dp))
    labels.forEach { label ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(Modifier.size(10.dp).background(markerColor(label), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        "The dots move as the responders get closer. A grey dot means its location is out of date.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/**
 * One colour per responder, so a dot and its legend row match. Never red, amber or green: those
 * are the fire marker's severity colours.
 */
private val ResponderColors = listOf(
    Color(0xFF1A73E8), // blue
    Color(0xFF8E24AA), // purple
    Color(0xFF00838F), // cyan
    Color(0xFF6D4C41)  // brown
)

private fun markerColor(label: ResponderTrackingRules.MarkerLabel?): Color =
    if (label == null || !label.fresh) Gray600 else ResponderColors[label.position % ResponderColors.size]

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

// ---------- Previews ----------

private fun sample(status: IncidentStatus) = Incident(
    id = "demo",
    fireType = FireType.ELECTRICAL.value,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    status = status.value,
    severity = Severity.HIGH.value
)

@Composable
private fun PreviewScreen(state: StatusUiState) {
    FIRESTheme { StatusContent(state = state, onBack = {}, onOpenChat = {}, onRetry = {}) }
}

@Preview(name = "Status - reported", showSystemUi = true)
@Composable
private fun StatusReportedPreview() =
    PreviewScreen(StatusUiState(sample(IncidentStatus.REPORTED), DeliveryState.SENT, isLoading = false))

private fun tracked(agoSeconds: Long = 5, eta: Int? = 360) = ResponderLocation(
    id = "r1", latitude = 14.5990, longitude = 120.9830,
    etaSeconds = eta, distanceMeters = 2_400,
    updatedAt = com.google.firebase.Timestamp(System.currentTimeMillis() / 1000 - agoSeconds, 0)
)

@Preview(name = "Status - dispatched, responder live", showSystemUi = true)
@Composable
private fun StatusLivePreview() = PreviewScreen(
    StatusUiState(
        sample(IncidentStatus.DISPATCHED), DeliveryState.SENT, isLoading = false,
        responders = listOf(tracked()), nowMillis = System.currentTimeMillis()
    )
)

@Preview(name = "Status - dispatched, location stale", showSystemUi = true)
@Composable
private fun StatusStalePreview() = PreviewScreen(
    StatusUiState(
        sample(IncidentStatus.DISPATCHED), DeliveryState.SENT, isLoading = false,
        responders = listOf(tracked(agoSeconds = 125)), nowMillis = System.currentTimeMillis()
    )
)

@Preview(name = "Status - dispatched, waiting for location", showSystemUi = true)
@Composable
private fun StatusWaitingPreview() = PreviewScreen(
    StatusUiState(sample(IncidentStatus.DISPATCHED), DeliveryState.SENT, isLoading = false)
)

@Preview(name = "Status - on scene", showSystemUi = true)
@Composable
private fun StatusOnScenePreview() =
    PreviewScreen(StatusUiState(sample(IncidentStatus.ON_SCENE), DeliveryState.SENT, isLoading = false))

@Preview(name = "Status - not sent yet (offline)", showSystemUi = true)
@Composable
private fun StatusSendingPreview() =
    PreviewScreen(StatusUiState(sample(IncidentStatus.REPORTED), DeliveryState.SENDING, isLoading = false))

@Preview(name = "Status - closed", showSystemUi = true)
@Composable
private fun StatusDismissedPreview() =
    PreviewScreen(StatusUiState(sample(IncidentStatus.DISMISSED), DeliveryState.SENT, isLoading = false))

@Preview(name = "Status - load error", showSystemUi = true)
@Composable
private fun StatusErrorPreview() =
    PreviewScreen(StatusUiState(isLoading = false, error = StatusViewModel.LOAD_ERROR))
