package com.example.fires.ui.citizen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.ChatRules
import com.example.fires.util.timeAgoLabel
import com.example.fires.viewmodel.MyReportsUiState
import com.example.fires.viewmodel.MyReportsViewModel

/**
 * My reports (D7): every report this account has sent, newest first. The list is live, so a
 * status change made by a responder shows up here without refreshing.
 *
 * @param onOpen opens the status screen of the tapped report (gets the incident id).
 */
@Composable
fun MyReportsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: MyReportsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyReportsContent(state = state, onBack = onBack, onOpen = onOpen, onRetry = viewModel::retry)
}

/** The look of the list. No ViewModel here, so it can be previewed. */
@Composable
fun MyReportsContent(
    state: MyReportsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("My reports", style = MaterialTheme.typography.headlineSmall)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    state.reports.isEmpty() -> Text(
                        text = "You haven't sent any reports yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )

                    else -> {
                        val now = System.currentTimeMillis()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.reports, key = { it.id }) { report ->
                                ReportCard(report = report, nowMillis = now, onClick = { onOpen(report.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: Incident, nowMillis: Long, onClick: () -> Unit) {
    val sentAt = report.submittedAt?.toDate()?.time
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = report.fireTypeEnum().label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(report.statusEnum())
            }
            if (report.addressText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = report.addressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // No server time yet means the report is still only saved on this phone.
                    text = sentAt?.let { timeAgoLabel(it, nowMillis) } ?: ChatRules.SENDING_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SeverityChip(report.severityEnum())
            }
        }
    }
}

// ---------- Previews ----------

private fun sample(id: String, type: FireType, status: IncidentStatus, severity: Severity) = Incident(
    id = id,
    fireType = type.value,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    status = status.value,
    severity = severity.value
)

@Preview(name = "My reports - list", showSystemUi = true)
@Composable
private fun MyReportsListPreview() {
    FIRESTheme {
        MyReportsContent(
            state = MyReportsUiState(
                reports = listOf(
                    sample("1", FireType.ELECTRICAL, IncidentStatus.DISPATCHED, Severity.HIGH),
                    sample("2", FireType.RUBBISH, IncidentStatus.RESOLVED, Severity.LOW)
                ),
                isLoading = false
            ),
            onBack = {}, onOpen = {}, onRetry = {}
        )
    }
}

@Preview(name = "My reports - empty", showSystemUi = true)
@Composable
private fun MyReportsEmptyPreview() {
    FIRESTheme {
        MyReportsContent(MyReportsUiState(isLoading = false), onBack = {}, onOpen = {}, onRetry = {})
    }
}

@Preview(name = "My reports - error", showSystemUi = true)
@Composable
private fun MyReportsErrorPreview() {
    FIRESTheme {
        MyReportsContent(
            MyReportsUiState(isLoading = false, error = MyReportsViewModel.LOAD_ERROR),
            onBack = {}, onOpen = {}, onRetry = {}
        )
    }
}
