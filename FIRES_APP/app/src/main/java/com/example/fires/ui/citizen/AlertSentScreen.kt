package com.example.fires.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.fires.ui.common.SectionLabel
import com.example.fires.ui.common.SoftCard
import com.example.fires.ui.common.Tone
import com.example.fires.ui.common.TonePill
import com.example.fires.ui.common.ToneBanner
import com.example.fires.ui.common.toneBackground
import com.example.fires.ui.common.toneContent
import com.example.fires.ui.theme.Green
import com.example.fires.util.SentRules
import com.example.fires.util.SentStep
import com.example.fires.util.SentStepState
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

/**
 * Confirmation after a report is sent (D6, round 2 design). It never claims more than is true:
 *  - Sending: the report is saved on this phone and goes out by itself when there is signal
 *  - Sent: the server has it, so responders can see it, and an administrator verifies it next
 *  - Not sent: the server refused it, and the person is told to send it again
 *
 * @param incidentId the report to follow.
 * @param onBack back to the home screen.
 * @param onViewStatus opens the live status screen for this report.
 * @param onAddMessage opens the chat of this report.
 */
@Composable
fun AlertSentScreen(
    incidentId: String,
    onBack: () -> Unit,
    onViewStatus: () -> Unit,
    onAddMessage: () -> Unit,
    viewModel: AlertSentViewModel = viewModel(factory = AlertSentViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertSentContent(state = state, onBack = onBack, onViewStatus = onViewStatus, onAddMessage = onAddMessage)
}

/** The look of the confirmation screen. No ViewModel here, so it can be previewed. */
@Composable
fun AlertSentContent(
    state: AlertSentUiState,
    onBack: () -> Unit,
    onViewStatus: () -> Unit,
    onAddMessage: () -> Unit
) {
    val failed = state.delivery == DeliveryState.FAILED

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                StatusCircle(state.delivery)
                Spacer(Modifier.height(14.dp))

                // A live region, so TalkBack reads it out when it changes from "saving" to "received".
                Column(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        SentRules.title(state.delivery),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        SentRules.subtitle(state.delivery),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                }

                if (!failed) {
                    SentRules.chip(state.code, state.fireTypeLabel)?.let {
                        Spacer(Modifier.height(12.dp))
                        TonePill(it, Tone.GREY)
                    }
                }

                if (state.isDuplicate && !failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Someone nearby has already reported a fire. Your report was added to it, " +
                            "so responders have both.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!failed) {
                    Spacer(Modifier.height(24.dp))
                    NextStepsCard(SentRules.steps(state.delivery, state.sentAtMillis, state.hasPhoto))
                    Spacer(Modifier.height(16.dp))
                    ToneBanner(
                        text = SentRules.KEEP_PHONE_NEARBY,
                        tone = Tone.AMBER,
                        icon = Icons.Filled.Notifications
                    )
                }
            }

            // ---- Actions ----
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp)) {
                // A report that was refused has nothing to follow.
                if (!failed) {
                    PrimaryButton(text = "Track live status", onClick = onViewStatus)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(text = "Add a message", onClick = onAddMessage)
                } else {
                    SecondaryButton(text = "Back to home", onClick = onBack)
                }
            }
        }
    }
}

/** The big round icon on top: a tick when received, a spinner while saving, a warning when refused. */
@Composable
private fun StatusCircle(delivery: DeliveryState) {
    val tone = when (delivery) {
        DeliveryState.SENT -> Tone.GREEN
        DeliveryState.SENDING -> Tone.AMBER
        DeliveryState.FAILED -> Tone.RED
    }
    Box(
        modifier = Modifier.size(84.dp).background(toneBackground(tone), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (delivery) {
            DeliveryState.SENT -> Icon(Icons.Filled.Check, contentDescription = null, tint = toneContent(tone), modifier = Modifier.size(44.dp))
            DeliveryState.SENDING -> CircularProgressIndicator(
                modifier = Modifier.size(36.dp), strokeWidth = 3.dp, color = toneContent(tone)
            )
            DeliveryState.FAILED -> Icon(Icons.Filled.Warning, contentDescription = null, tint = toneContent(tone), modifier = Modifier.size(40.dp))
        }
    }
}

/** "WHAT HAPPENS NEXT": the four steps with a line between them. */
@Composable
private fun NextStepsCard(steps: List<SentStep>) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            SectionLabel("WHAT HAPPENS NEXT")
            Spacer(Modifier.height(14.dp))
            steps.forEachIndexed { index, step ->
                StepRow(step)
                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(start = 13.dp, top = 4.dp, bottom = 4.dp)
                            .width(2.dp)
                            .height(18.dp)
                            .background(
                                if (step.state == SentStepState.DONE) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: SentStep) {
    val muted = step.state == SentStepState.TODO
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            when (step.state) {
                SentStepState.DONE -> Box(
                    modifier = Modifier.size(28.dp).background(Green, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp)) }
                SentStepState.NOW -> Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                SentStepState.TODO -> Box(
                    modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- Previews ----------

private val previewSpot = LatLon(14.5995, 120.9842)

@Composable
private fun PreviewScreen(state: AlertSentUiState) {
    FIRESTheme { AlertSentContent(state = state, onBack = {}, onViewStatus = {}, onAddMessage = {}) }
}

@Preview(name = "Alert sent - sent", showSystemUi = true)
@Composable
private fun AlertSentSentPreview() = PreviewScreen(
    AlertSentUiState(DeliveryState.SENT, previewSpot, Severity.HIGH, code = "INC-1042", fireTypeLabel = "House fire", hasPhoto = true)
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
