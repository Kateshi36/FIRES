package com.example.fires.ui.responder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.fires.data.model.Verification
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.ChipPill
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.statusColor
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.DatePreset
import com.example.fires.util.DateRange
import com.example.fires.util.HistoryRules
import com.example.fires.util.HistoryStatusFilter
import com.example.fires.util.dateTimeLabel
import com.example.fires.viewmodel.HistoryItem
import com.example.fires.viewmodel.HistoryUiState
import com.example.fires.viewmodel.HistoryViewModel
import com.google.firebase.Timestamp
import java.time.LocalDate

/**
 * Incident history (E7): closed reports, newest first, that a responder can narrow down by how they
 * ended (status) and by the day they were closed (date). Tapping one opens its detail screen.
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenIncident: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(
        state = state,
        onBack = onBack,
        onStatusFilter = viewModel::setStatusFilter,
        onDateRange = viewModel::setDateRange,
        onClearFilters = viewModel::clearFilters,
        onRetry = viewModel::retry,
        onOpenIncident = onOpenIncident
    )
}

/** The look of the history screen. No ViewModel here, so it can be previewed. */
@Composable
fun HistoryContent(
    state: HistoryUiState,
    onBack: () -> Unit,
    onStatusFilter: (HistoryStatusFilter) -> Unit,
    onDateRange: (DateRange) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onOpenIncident: (String) -> Unit
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
                Text("Incident history", style = MaterialTheme.typography.headlineSmall)
            }

            // ---- Date filter ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilter(range = state.dateRange, onChange = onDateRange)
                Spacer(Modifier.weight(1f))
                if (state.isFiltered) TextButton(onClick = onClearFilters) { Text("Clear filters") }
            }

            // ---- Status filter ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { onStatusFilter(filter) },
                        label = { Text("${filter.label} ${state.counts[filter] ?: 0}") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    state.items.isEmpty() -> EmptyNotice(state, onClearFilters)

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.items, key = { it.incident.id }) { item ->
                            HistoryCard(item, onClick = { onOpenIncident(item.incident.id) })
                        }
                    }
                }
            }
        }
    }
}

/** One chip that shows the chosen days. Tapping it offers quick ranges or the calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilter(range: DateRange, onChange: (DateRange) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = !range.isAny,
            onClick = { menuOpen = true },
            label = { Text(HistoryRules.rangeLabel(range)) },
            leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DatePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        menuOpen = false
                        onChange(preset.range(LocalDate.now()))
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Choose dates…") },
                onClick = {
                    menuOpen = false
                    pickerOpen = true
                }
            )
        }
    }

    if (pickerOpen) {
        val picker = rememberDateRangePickerState()
        val start = picker.selectedStartDateMillis
        val end = picker.selectedEndDateMillis
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    enabled = start != null,
                    onClick = {
                        pickerOpen = false
                        if (start != null) {
                            val from = HistoryRules.dateFromPicker(start)
                            // Only one day tapped: that single day.
                            val to = end?.let(HistoryRules::dateFromPicker) ?: from
                            onChange(DateRange(from, to))
                        }
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = picker, modifier = Modifier.height(480.dp))
        }
    }
}

@Composable
private fun EmptyNotice(state: HistoryUiState, onClearFilters: () -> Unit) {
    // Two different situations, two different messages.
    val filtered = state.total > 0 && state.isFiltered
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = if (filtered) "No incidents match these filters." else "No closed incidents yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            if (filtered) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Clear filters", onClick = onClearFilters)
            }
        }
    }
}

/** One closed report: type, how it ended, address, when it was closed, and the remarks if any. */
@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit) {
    val incident = item.incident
    val closedAt = HistoryRules.closedAtMillis(incident)

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
                    text = incident.fireTypeEnum().label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ChipPill(HistoryRules.outcomeLabel(incident), statusColor(incident.statusEnum()))
            }
            if (incident.addressText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = incident.addressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!item.remarks.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.remarks,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Closed " + (closedAt?.let(::dateTimeLabel) ?: "just now"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- Previews ----------

private fun sample(
    id: String,
    type: FireType,
    status: IncidentStatus,
    verification: Verification,
    address: String,
    closedSeconds: Long
) = Incident(
    id = id, fireType = type.value, addressText = address, status = status.value,
    verification = verification.value, severity = Severity.HIGH.value,
    updatedAt = Timestamp(closedSeconds, 0)
)

private val previewItems = listOf(
    HistoryItem(
        sample("1", FireType.STRUCTURAL, IncidentStatus.RESOLVED, Verification.VERIFIED,
            "123 Rizal St., Purok 3, Bagumbayan", 1_790_000_000),
        remarks = "Fire put out by B-FLARE and BFP at 3:40 PM. No injuries. Kitchen wall damaged."
    ),
    HistoryItem(
        sample("2", FireType.RUBBISH, IncidentStatus.DISMISSED, Verification.FALSE,
            "Mabini St., Purok 1", 1_789_900_000),
        remarks = null
    ),
    HistoryItem(
        sample("3", FireType.ELECTRICAL, IncidentStatus.DISMISSED, Verification.DUPLICATE,
            "123 Rizal St., Purok 3, Bagumbayan", 1_789_800_000),
        remarks = null
    )
)

private val previewCounts = mapOf(
    HistoryStatusFilter.ALL to 3, HistoryStatusFilter.RESOLVED to 1,
    HistoryStatusFilter.FALSE_REPORT to 1, HistoryStatusFilter.DUPLICATE to 1
)

@Composable
private fun PreviewScreen(state: HistoryUiState) {
    FIRESTheme {
        HistoryContent(
            state = state, onBack = {}, onStatusFilter = {}, onDateRange = {},
            onClearFilters = {}, onRetry = {}, onOpenIncident = {}
        )
    }
}

@Preview(name = "History - list", showSystemUi = true)
@Composable
private fun HistoryListPreview() = PreviewScreen(
    HistoryUiState(items = previewItems, counts = previewCounts, total = 3, isLoading = false)
)

@Preview(name = "History - filtered", showSystemUi = true)
@Composable
private fun HistoryFilteredPreview() = PreviewScreen(
    HistoryUiState(
        items = previewItems.take(1), counts = previewCounts, total = 3, isLoading = false,
        statusFilter = HistoryStatusFilter.RESOLVED,
        dateRange = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
    )
)

@Preview(name = "History - nothing matches", showSystemUi = true)
@Composable
private fun HistoryNoMatchPreview() = PreviewScreen(
    HistoryUiState(
        counts = previewCounts.mapValues { 0 }, total = 3, isLoading = false,
        statusFilter = HistoryStatusFilter.DUPLICATE,
        dateRange = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7))
    )
)

@Preview(name = "History - none yet", showSystemUi = true)
@Composable
private fun HistoryEmptyPreview() = PreviewScreen(HistoryUiState(isLoading = false))

@Preview(name = "History - error", showSystemUi = true)
@Composable
private fun HistoryErrorPreview() =
    PreviewScreen(HistoryUiState(isLoading = false, error = HistoryViewModel.LOAD_ERROR))
