package com.example.fires.ui.citizen

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.Severity
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.MapPoint
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.severityColor
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.FireRed
import com.example.fires.viewmodel.AlertSentUiState
import com.example.fires.viewmodel.AlertSentViewModel

/** Philippine national emergency number. Swap in the B-FLARE / BDRRMO hotline once it is confirmed. */
private const val EMERGENCY_NUMBER = "911"

/**
 * Confirmation after a report is sent (D6). It never claims more than is true:
 *  - Sending: the report is saved on this phone and goes out by itself when there is signal
 *  - Sent: the server has it, so responders can see it
 *  - Not sent: the server refused it, and the person is told to send it again or call
 *
 * @param incidentId the report to follow.
 * @param onBack back to the home screen.
 * @param onViewStatus opens the live status screen for this report.
 */
@Composable
fun AlertSentScreen(
    incidentId: String,
    onBack: () -> Unit,
    onViewStatus: () -> Unit,
    viewModel: AlertSentViewModel = viewModel(factory = AlertSentViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertSentContent(state = state, onBack = onBack, onViewStatus = onViewStatus)
}

/** The look of the confirmation screen. No ViewModel here, so it can be previewed. */
@Composable
fun AlertSentContent(
    state: AlertSentUiState,
    onBack: () -> Unit,
    onViewStatus: () -> Unit
) {
    val title = when (state.delivery) {
        DeliveryState.SENDING -> "Sending report"
        DeliveryState.SENT -> "Alert sent"
        DeliveryState.FAILED -> "Report not sent"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                }
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                DeliveryNotice(state.delivery)

                if (state.isDuplicate && state.delivery != DeliveryState.FAILED) {
                    Spacer(Modifier.height(12.dp))
                    BodyText(
                        "Someone nearby has already reported a fire. Your report was added to it, " +
                            "so responders have both."
                    )
                }

                state.location?.let { point ->
                    Spacer(Modifier.height(16.dp))
                    ReportedSpotMap(point = point, severity = state.severity)
                }

                if (state.delivery == DeliveryState.SENDING) {
                    Spacer(Modifier.height(16.dp))
                    BodyText(
                        "Your report is saved on this phone and will be sent automatically as soon " +
                            "as you are back online. Keep the app open if you can. " +
                            "If it is urgent, call $EMERGENCY_NUMBER."
                    )
                }
            }

            // ---- Actions ----
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                // A report that was refused has nothing to follow.
                if (state.delivery != DeliveryState.FAILED) {
                    PrimaryButton(text = "View status", onClick = onViewStatus)
                    Spacer(Modifier.height(12.dp))
                }
                SecondaryButton(text = "Back to home", onClick = onBack)
            }
        }
    }
}

/**
 * The status message at the top. It is a live region, so TalkBack reads it out when it changes
 * from "waiting" to "sent".
 */
@Composable
private fun DeliveryNotice(delivery: DeliveryState) {
    val announce = Modifier
        .fillMaxWidth()
        .semantics { liveRegion = LiveRegionMode.Polite }

    when (delivery) {
        DeliveryState.SENT -> Surface(
            modifier = announce,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sent to B-FLARE and BDRRMO", style = MaterialTheme.typography.titleMedium)
            }
        }

        DeliveryState.SENDING -> Surface(
            modifier = announce,
            shape = RoundedCornerShape(12.dp),
            color = Amber.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text("Waiting for a connection", style = MaterialTheme.typography.titleMedium)
            }
        }

        DeliveryState.FAILED -> ErrorBanner(
            "We couldn't deliver your report. Please go back and send it again. " +
                "If it is urgent, call $EMERGENCY_NUMBER."
        )
    }
}

/** A small map showing where the report is, with the marker colored by its severity. */
@Composable
private fun ReportedSpotMap(point: LatLon, severity: Severity?) {
    FiresMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
        center = point,
        markers = listOf(
            MapPoint(
                id = "report",
                latitude = point.latitude,
                longitude = point.longitude,
                title = "Reported fire",
                color = severity?.let { severityColor(it) } ?: FireRed
            )
        )
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------- Previews ----------

private val previewSpot = LatLon(14.5995, 120.9842)

@Composable
private fun PreviewScreen(state: AlertSentUiState) {
    FIRESTheme { AlertSentContent(state = state, onBack = {}, onViewStatus = {}) }
}

@Preview(name = "Alert sent - sent", showSystemUi = true)
@Composable
private fun AlertSentSentPreview() = PreviewScreen(
    AlertSentUiState(DeliveryState.SENT, previewSpot, Severity.HIGH)
)

@Preview(name = "Alert sent - sending (offline)", showSystemUi = true)
@Composable
private fun AlertSentSendingPreview() = PreviewScreen(
    AlertSentUiState(DeliveryState.SENDING, previewSpot, Severity.CRITICAL)
)

@Preview(name = "Alert sent - duplicate of a nearby report", showSystemUi = true)
@Composable
private fun AlertSentDuplicatePreview() = PreviewScreen(
    AlertSentUiState(DeliveryState.SENT, previewSpot, Severity.MEDIUM, isDuplicate = true)
)

@Preview(name = "Alert sent - not delivered", showSystemUi = true)
@Composable
private fun AlertSentFailedPreview() = PreviewScreen(AlertSentUiState(DeliveryState.FAILED))
