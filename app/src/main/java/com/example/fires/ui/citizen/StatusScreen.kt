package com.example.fires.ui.citizen

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
import androidx.compose.ui.Modifier
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
import com.example.fires.data.model.Severity
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.common.StatusStepper
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.ChatRules
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

                    else -> ReportDetails(incident = incident, delivery = state.delivery)
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
private fun ReportDetails(incident: Incident, delivery: DeliveryState) {
    val status = incident.statusEnum()

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

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityChip(incident.severityEnum())
            StatusChip(status)
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
