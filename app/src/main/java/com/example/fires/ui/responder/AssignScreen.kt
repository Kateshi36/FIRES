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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.example.fires.ui.common.ChipGrid
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.RadioChoiceRow
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.AssignmentRules
import com.example.fires.viewmodel.AssignUiState
import com.example.fires.viewmodel.AssignViewModel

/**
 * Assign responders / resources (E4). The responder picks the unit that is going and, if any, the
 * resources it brings, and saves. The assignment then shows on the incident detail screen.
 *
 * @param onDone called once the assignment is saved, so the caller can go back.
 */
@Composable
fun AssignScreen(
    incidentId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AssignViewModel = viewModel(factory = AssignViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

    AssignContent(
        state = state,
        onBack = onBack,
        onSelectUnit = viewModel::onUnitSelect,
        onToggleResource = viewModel::onResourceToggle,
        onAssign = viewModel::onAssignClick
    )
}

/** The look of the assign screen. No ViewModel here, so it can be previewed. */
@Composable
fun AssignContent(
    state: AssignUiState,
    onBack: () -> Unit,
    onSelectUnit: (String) -> Unit,
    onToggleResource: (String) -> Unit,
    onAssign: () -> Unit
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
                Text("Assign responders", style = MaterialTheme.typography.headlineSmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    incident == null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner("We couldn't find this incident. It may have been removed.")
                    }

                    else -> Form(state, incident, onSelectUnit, onToggleResource)
                }
            }

            if (incident != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    PrimaryButton(
                        text = "Assign",
                        onClick = onAssign,
                        enabled = state.canAssign,
                        loading = state.isSaving
                    )
                }
            }
        }
    }
}

@Composable
private fun Form(
    state: AssignUiState,
    incident: Incident,
    onSelectUnit: (String) -> Unit,
    onToggleResource: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Which incident this is for, so the responder is never unsure what they are assigning to.
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

        if (!state.canAssign) ErrorBanner(AssignmentRules.MSG_CLOSED)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Who is responding?", style = MaterialTheme.typography.titleSmall)
            AssignmentRules.UNITS.forEach { unit ->
                RadioChoiceRow(
                    text = unit,
                    selected = state.unit == unit,
                    onClick = { onSelectUnit(unit) },
                    enabled = state.canAssign && !state.isSaving
                )
            }
            if (state.unitError != null) {
                Text(
                    text = state.unitError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resources (optional)", style = MaterialTheme.typography.titleSmall)
            ChipGrid(
                items = AssignmentRules.RESOURCES,
                isSelected = { it in state.resources },
                label = { it },
                onToggle = onToggleResource
            )
        }

        if (state.error != null) ErrorBanner(state.error)

        Spacer(Modifier.height(4.dp))
    }
}

// ---------- Previews ----------

private fun sample(status: IncidentStatus = IncidentStatus.VERIFIED) = Incident(
    id = "demo",
    fireType = FireType.STRUCTURAL.value,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    status = status.value,
    severity = Severity.CRITICAL.value
)

@Composable
private fun PreviewScreen(state: AssignUiState) {
    FIRESTheme {
        AssignContent(state = state, onBack = {}, onSelectUnit = {}, onToggleResource = {}, onAssign = {})
    }
}

@Preview(name = "Assign - choices made", showSystemUi = true)
@Composable
private fun AssignChosenPreview() = PreviewScreen(
    AssignUiState(
        incident = sample(), isLoading = false,
        unit = "BFP", resources = setOf("Fire truck")
    )
)

@Preview(name = "Assign - nothing chosen", showSystemUi = true)
@Composable
private fun AssignEmptyPreview() = PreviewScreen(
    AssignUiState(incident = sample(), isLoading = false, unitError = AssignmentRules.MSG_UNIT_REQUIRED)
)

@Preview(name = "Assign - not confirmed", showSystemUi = true)
@Composable
private fun AssignErrorPreview() = PreviewScreen(
    AssignUiState(
        incident = sample(), isLoading = false, unit = "BDRRMO",
        error = AssignmentRules.MSG_NOT_CONFIRMED
    )
)

@Preview(name = "Assign - report closed", showSystemUi = true)
@Composable
private fun AssignClosedPreview() =
    PreviewScreen(AssignUiState(incident = sample(IncidentStatus.RESOLVED), isLoading = false))
