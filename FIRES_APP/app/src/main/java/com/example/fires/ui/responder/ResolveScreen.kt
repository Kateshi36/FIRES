package com.example.fires.ui.responder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.LabeledTextField
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.ResolveRules
import com.example.fires.viewmodel.ResolveUiState
import com.example.fires.viewmodel.ResolveViewModel

/**
 * Resolve incident (E5). The responder writes the remarks and resolves. That creates the
 * historical record and closes the report in one step.
 *
 * @param onDone called once the report is resolved, so the caller can go back.
 */
@Composable
fun ResolveScreen(
    incidentId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ResolveViewModel = viewModel(factory = ResolveViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) { viewModel.done.collect { currentOnDone() } }

    ResolveContent(
        state = state,
        onBack = onBack,
        onRemarksChange = viewModel::onRemarksChange,
        onResolve = viewModel::onResolveClick
    )
}

/** The look of the resolve screen. No ViewModel here, so it can be previewed. */
@Composable
fun ResolveContent(
    state: ResolveUiState,
    onBack: () -> Unit,
    onRemarksChange: (String) -> Unit,
    onResolve: () -> Unit
) {
    val incident = state.incident

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !state.isSaving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Resolve incident", style = MaterialTheme.typography.headlineSmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    incident == null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner("We couldn't find this incident. It may have been removed.")
                    }

                    else -> Form(state, incident, onRemarksChange)
                }
            }

            if (incident != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    PrimaryButton(
                        text = "Resolve incident",
                        onClick = onResolve,
                        enabled = state.canResolve,
                        loading = state.isSaving
                    )
                }
            }
        }
    }
}

@Composable
private fun Form(state: ResolveUiState, incident: Incident, onRemarksChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Which incident this is for, so the responder is never unsure what they are closing.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Border),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(incident.fireTypeEnum().label, style = MaterialTheme.typography.titleMedium)
                if (incident.addressText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        incident.addressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeverityChip(incident.severityEnum())
                    StatusChip(incident.statusEnum())
                }
            }
        }

        if (!state.canResolve) ErrorBanner(ResolveRules.MSG_NOT_ON_SCENE)

        Column {
            LabeledTextField(
                label = "Remarks",
                value = state.remarks,
                onValueChange = onRemarksChange,
                placeholder = "What happened, how it was put out, any damage or injuries",
                error = state.remarksError,
                singleLine = false,
                minLines = 5,
                enabled = state.canResolve && !state.isSaving
            )
            Text(
                text = "${state.remarks.trim().length} / ${ResolveRules.MAX_REMARKS}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }

        Text(
            "Resolving closes this report and saves the remarks in the incident records. " +
                "It leaves the dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.error != null) ErrorBanner(state.error)

        Spacer(Modifier.height(4.dp))
    }
}

// ---------- Previews ----------

private fun sample(status: IncidentStatus = IncidentStatus.ON_SCENE) = Incident(
    id = "demo",
    fireType = FireType.STRUCTURAL.value,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    status = status.value,
    severity = Severity.CRITICAL.value
)

@Composable
private fun PreviewScreen(state: ResolveUiState) {
    FIRESTheme { ResolveContent(state = state, onBack = {}, onRemarksChange = {}, onResolve = {}) }
}

@Preview(name = "Resolve - empty", showSystemUi = true)
@Composable
private fun ResolveEmptyPreview() = PreviewScreen(ResolveUiState(incident = sample(), isLoading = false))

@Preview(name = "Resolve - remarks missing", showSystemUi = true)
@Composable
private fun ResolveRemarksErrorPreview() = PreviewScreen(
    ResolveUiState(incident = sample(), isLoading = false, remarksError = ResolveRules.MSG_REMARKS_BLANK)
)

@Preview(name = "Resolve - saving", showSystemUi = true)
@Composable
private fun ResolveSavingPreview() = PreviewScreen(
    ResolveUiState(
        incident = sample(), isLoading = false, isSaving = true, attempted = true,
        remarks = "Fire put out by B-FLARE and BFP at 3:40 PM. No injuries. Kitchen wall damaged."
    )
)

@Preview(name = "Resolve - not confirmed", showSystemUi = true)
@Composable
private fun ResolveNotConfirmedPreview() = PreviewScreen(
    ResolveUiState(
        incident = sample(IncidentStatus.RESOLVED), isLoading = false, attempted = true,
        remarks = "Fire put out by B-FLARE and BFP at 3:40 PM. No injuries.",
        error = ResolveRules.MSG_NOT_CONFIRMED
    )
)

@Preview(name = "Resolve - not on scene", showSystemUi = true)
@Composable
private fun ResolveNotOnScenePreview() =
    PreviewScreen(ResolveUiState(incident = sample(IncidentStatus.DISPATCHED), isLoading = false))
