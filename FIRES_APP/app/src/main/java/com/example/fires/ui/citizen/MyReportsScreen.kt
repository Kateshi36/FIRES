package com.example.fires.ui.citizen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.RoundHeader
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SectionLabel
import com.example.fires.ui.common.SegmentBar
import com.example.fires.ui.common.SoftCard
import com.example.fires.ui.common.Tone
import com.example.fires.ui.common.TonePill
import com.example.fires.ui.common.ToneBanner
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.FireRedDark
import com.example.fires.ui.theme.FireRedSoft
import com.example.fires.util.ChatRules
import com.example.fires.util.MyReportsRules
import com.example.fires.util.ReportFilter
import com.example.fires.util.ReportGroup
import com.example.fires.util.incidentCode
import com.example.fires.viewmodel.MyReportsUiState
import com.example.fires.viewmodel.MyReportsViewModel

/**
 * My reports (D7, round 2 design): the open report as a big card with a progress bar, then
 * filter chips and the rest grouped by month. Live, so a status change made by a responder shows
 * up here without refreshing.
 *
 * @param onOpen opens the status screen of the tapped report (gets the incident id).
 * @param bottomBar the citizen tab bar, passed in by the navigation.
 */
@Composable
fun MyReportsScreen(
    onOpen: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: MyReportsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyReportsContent(state = state, onOpen = onOpen, onRetry = viewModel::retry, bottomBar = bottomBar)
}

/** The look of the list. No ViewModel here, so it can be previewed. */
@Composable
fun MyReportsContent(
    state: MyReportsUiState,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    var filter by rememberSaveable { mutableStateOf(ReportFilter.ALL) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                RoundHeader(title = "My reports", sub = "Barangay Bagumbayan")

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

                        else -> ReportsList(state.reports, filter, { filter = it }, onOpen)
                    }
                }
            }
            bottomBar()
        }
    }
}

@Composable
private fun ReportsList(
    reports: List<Incident>,
    filter: ReportFilter,
    onFilter: (ReportFilter) -> Unit,
    onOpen: (String) -> Unit
) {
    val now = System.currentTimeMillis()
    val pinned = MyReportsRules.pinned(reports)
    val counts = MyReportsRules.counts(reports)
    val grouped = MyReportsRules.listed(reports, filter).groupBy { MyReportsRules.group(it, now) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pinned != null) {
            item(key = "pinned") { ActiveReportCard(pinned, onClick = { onOpen(pinned.id) }) }
        }
        item(key = "filters") {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportFilter.entries.forEach { f ->
                    FilterChipPill(
                        label = "${f.label} ${counts[f] ?: 0}",
                        selected = f == filter,
                        onClick = { onFilter(f) }
                    )
                }
            }
        }
        ReportGroup.entries.forEach { group ->
            val rows = grouped[group].orEmpty()
            if (rows.isNotEmpty()) {
                item(key = "label-${group.name}") {
                    SectionLabel(group.label, modifier = Modifier.padding(top = 6.dp))
                }
                rows.forEach { report ->
                    item(key = report.id) { ReportRow(report, onClick = { onOpen(report.id) }) }
                }
            }
        }
    }
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/** The report that is still open: progress bar, one line on what is happening, and "Track live". */
@Composable
private fun ActiveReportCard(report: Incident, onClick: () -> Unit) {
    val status = report.statusEnum()
    val sentAt = report.submittedAt?.toDate()?.time
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonePill("Active report", Tone.RED, icon = Icons.Filled.LocalFireDepartment)
                Spacer(Modifier.weight(1f))
                Text(
                    text = sentAt?.let { "Sent ${MyReportsRules.timeLabel(it)}" } ?: ChatRules.SENDING_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                Text(
                    report.addressText.ifBlank { "Location on the map" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${report.fireTypeEnum().label} · ${incidentCode(report.id)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SegmentBar(filled = MyReportsRules.progressSegments(status), total = 4)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MyReportsRules.PROGRESS_LABELS.forEachIndexed { index, label ->
                    val current = index + 1 == MyReportsRules.progressSegments(status)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ToneBanner(
                text = MyReportsRules.bannerText(status),
                tone = if (MyReportsRules.bannerIsGood(status)) Tone.GREEN else Tone.GREY,
                icon = Icons.Filled.Navigation
            )
            PrimaryButton(text = "Track live", onClick = onClick)
        }
    }
}

@Composable
private fun ReportRow(report: Incident, onClick: () -> Unit) {
    val status = report.statusEnum()
    val sentAt = report.submittedAt?.toDate()?.time
    val meta = buildString {
        append(incidentCode(report.id))
        append(" · ")
        if (sentAt != null) append(MyReportsRules.dayLabel(sentAt)).append(", ")
        append(report.fireTypeEnum().label.lowercase())
    }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LocalFireDepartment, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        report.addressText.ifBlank { "Location on the map" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                TonePill(
                    MyReportsRules.outcomeLabel(status),
                    when (status) {
                        IncidentStatus.RESOLVED -> Tone.GREEN
                        IncidentStatus.DISMISSED -> Tone.GREY
                        IncidentStatus.ON_SCENE, IncidentStatus.DISPATCHED -> Tone.RED
                        else -> Tone.AMBER
                    }
                )
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
                    sample("1abcde", FireType.ELECTRICAL, IncidentStatus.DISPATCHED, Severity.HIGH),
                    sample("2abcde", FireType.RUBBISH, IncidentStatus.RESOLVED, Severity.LOW),
                    sample("3abcde", FireType.VEHICLE, IncidentStatus.DISMISSED, Severity.LOW)
                ),
                isLoading = false
            ),
            onOpen = {}, onRetry = {}
        )
    }
}

@Preview(name = "My reports - empty", showSystemUi = true)
@Composable
private fun MyReportsEmptyPreview() {
    FIRESTheme { MyReportsContent(MyReportsUiState(isLoading = false), onOpen = {}, onRetry = {}) }
}

@Preview(name = "My reports - error", showSystemUi = true)
@Composable
private fun MyReportsErrorPreview() {
    FIRESTheme {
        MyReportsContent(
            MyReportsUiState(isLoading = false, error = MyReportsViewModel.LOAD_ERROR),
            onOpen = {}, onRetry = {}
        )
    }
}
