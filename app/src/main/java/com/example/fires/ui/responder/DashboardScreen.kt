package com.example.fires.ui.responder

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.example.fires.ui.common.DEFAULT_MAP_CENTER
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.MapPoint
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SeverityChip
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.common.severityColor
import com.example.fires.ui.common.statusColor
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.BatteryOptimization
import com.example.fires.util.BatteryPromptRules
import com.example.fires.util.ChatRules
import com.example.fires.util.DashboardRules
import com.example.fires.util.DistanceFilter
import com.example.fires.util.IncidentGroup
import com.example.fires.util.StatusFilter
import com.example.fires.util.timeAgoLabel
import com.example.fires.viewmodel.DashboardTab
import com.example.fires.viewmodel.DashboardUiState
import com.example.fires.viewmodel.DashboardViewModel

/**
 * Responder dashboard (E1): every open incident on a live map and in a list, with filters.
 * Both views show the same filtered groups, so switching between them keeps the filters.
 *
 * @param onOpenIncident opens the detail screen of an incident (gets the incident id).
 * @param onHistory opens the history of resolved incidents.
 */
@Composable
fun DashboardScreen(
    onOpenIncident: (String) -> Unit,
    onHistory: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The distance filter and the "near me" labels need the responder's position.
    LaunchedEffect(Unit) { viewModel.refreshLocation(context) }

    // ---- Battery optimization (F1.13) ----
    // Whether Android still may put the app to sleep. Checked again every time the screen comes
    // back, because the responder changes it in a system dialog or in Settings, outside the app.
    var batteryIgnored by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryIgnored = BatteryOptimization.isIgnoring(context)
    }
    // rememberSaveable: turning the phone must not close the dialog before it has been answered.
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
    // First visit: ask once, and remember that we did, even if the app is closed before answering.
    LaunchedEffect(Unit) {
        if (BatteryPromptRules.shouldAskOnFirstVisit(
                isIgnoringOptimizations = BatteryOptimization.isIgnoring(context),
                alreadyAsked = BatteryOptimization.wasAsked(context)
            )
        ) {
            BatteryOptimization.markAsked(context)
            showBatteryDialog = true
        }
    }
    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onAllow = {
                showBatteryDialog = false
                if (!BatteryOptimization.requestAllow(context)) {
                    Toast.makeText(context, "Could not open the battery settings.", Toast.LENGTH_LONG).show()
                }
            },
            onNotNow = { showBatteryDialog = false }
        )
    }

    DashboardContent(
        state = state,
        onTab = viewModel::setTab,
        onStatusFilter = viewModel::setStatusFilter,
        onDistanceFilter = viewModel::setDistanceFilter,
        onSelect = viewModel::select,
        onClearSelection = viewModel::clearSelection,
        onRecenter = { viewModel.refreshLocation(context) },
        onRetry = viewModel::retry,
        onOpenIncident = onOpenIncident,
        onHistory = onHistory,
        onSignOut = onSignOut,
        showBatteryItem = BatteryPromptRules.showMenuItem(batteryIgnored),
        onBatteryClick = { showBatteryDialog = true }
    )
}

/** The look of the dashboard. No ViewModel here, so it can be previewed. */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onTab: (DashboardTab) -> Unit,
    onStatusFilter: (StatusFilter) -> Unit,
    onDistanceFilter: (DistanceFilter) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRecenter: () -> Unit,
    onRetry: () -> Unit,
    onOpenIncident: (String) -> Unit,
    onHistory: () -> Unit,
    onSignOut: () -> Unit,
    showBatteryItem: Boolean = false,
    onBatteryClick: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Incident history")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (showBatteryItem) {
                            DropdownMenuItem(
                                text = { Text("Battery optimization") },
                                onClick = {
                                    menuOpen = false
                                    onBatteryClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                menuOpen = false
                                confirmSignOut = true
                            }
                        )
                    }
                }
            }

            // ---- Map / List toggle and the location filter ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.tab == DashboardTab.MAP,
                    onClick = { onTab(DashboardTab.MAP) },
                    label = { Text("Map") }
                )
                FilterChip(
                    selected = state.tab == DashboardTab.LIST,
                    onClick = { onTab(DashboardTab.LIST) },
                    label = { Text("List") }
                )
                Spacer(Modifier.weight(1f))
                DistanceMenu(
                    selected = state.distanceFilter,
                    hasPosition = state.origin != null,
                    isLocating = state.isLocating,
                    onSelect = onDistanceFilter
                )
            }

            // ---- Status filter ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.statusFilter == filter,
                        onClick = { onStatusFilter(filter) },
                        label = { Text("${filter.label} ${state.counts[filter] ?: 0}") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Content ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    state.tab == DashboardTab.MAP -> MapView(
                        state = state,
                        onSelect = onSelect,
                        onClearSelection = onClearSelection,
                        onRecenter = onRecenter,
                        onOpenIncident = onOpenIncident
                    )

                    else -> ListView(state = state, onOpenIncident = onOpenIncident)
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You will need to log in again to see incidents.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Location filter ----------

@Composable
private fun DistanceMenu(
    selected: DistanceFilter,
    hasPosition: Boolean,
    isLocating: Boolean,
    onSelect: (DistanceFilter) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != DistanceFilter.ANY,
            onClick = { open = true },
            label = { Text(if (selected == DistanceFilter.ANY) "Near me" else selected.label) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DistanceFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    // A distance needs the responder's position. "Anywhere" always works.
                    enabled = option == DistanceFilter.ANY || hasPosition,
                    onClick = {
                        open = false
                        onSelect(option)
                    }
                )
            }
            if (!hasPosition) {
                Text(
                    text = if (isLocating) "Finding your location…" else "Turn on location to filter by distance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ---------- Map ----------

@Composable
private fun MapView(
    state: DashboardUiState,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRecenter: () -> Unit,
    onOpenIncident: (String) -> Unit
) {
    // One marker per group: duplicates are inside their primary's marker, not drawn again.
    val markers = state.groups.map { it.toMapPoint() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        FiresMap(
            modifier = Modifier.fillMaxSize(),
            center = state.origin ?: DEFAULT_MAP_CENTER,
            zoom = 15.0,
            markers = markers,
            userLocation = state.origin,
            onMarkerClick = onSelect,
            recenterKey = state.recenterKey
        )

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            shape = CircleShape,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            IconButton(onClick = onRecenter, enabled = !state.isLocating) {
                if (state.isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Go to my location")
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.groups.isEmpty()) {
                EmptyNotice(state)
            }
            val selected = state.selected
            if (selected != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        GroupCard(selected, state.origin, onClick = { onOpenIncident(selected.id) }, bordered = false)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onClearSelection) { Text("Close") }
                            Button(onClick = { onOpenIncident(selected.id) }) { Text("View details") }
                        }
                    }
                }
            } else {
                Legend()
            }
        }
    }
}

private fun IncidentGroup.toMapPoint(): MapPoint {
    val severity = primary.severityEnum()
    val status = primary.statusEnum()
    val extra = if (duplicates.isNotEmpty()) " +${duplicates.size} duplicate" else ""
    return MapPoint(
        id = id,
        latitude = primary.latitude,
        longitude = primary.longitude,
        title = "${severity.label} · ${status.label}$extra",
        color = severityColor(severity),
        ringColor = statusColor(status),
        highlighted = isFlagged
    )
}

/** Explains the marker colors: fill is severity, ring is status, dashed and big means flagged. */
@Composable
private fun Legend() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dot", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                Severity.entries.reversed().forEach { LegendDot(severityColor(it), it.label, ring = false) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ring", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                IncidentStatus.entries.filter { it.isActive }.forEach { LegendDot(statusColor(it), it.label, ring = true) }
            }
            Text(
                "Large dashed marker = flagged report",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, ring: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .then(
                    if (ring) Modifier.border(2.dp, color, CircleShape)
                    else Modifier.background(color)
                )
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------- List ----------

@Composable
private fun ListView(state: DashboardUiState, onOpenIncident: (String) -> Unit) {
    if (state.groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyNotice(state)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.groups, key = { it.id }) { group ->
            GroupCard(group, state.origin, onClick = { onOpenIncident(group.id) })
        }
    }
}

@Composable
private fun EmptyNotice(state: DashboardUiState) {
    // Two different situations, two different messages.
    val text = when {
        state.total == 0 -> "No active incidents right now."
        state.isFiltered -> "No incidents match these filters."
        else -> "No active incidents right now."
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * One incident group. A flagged report gets an amber outline and a "Flagged" label. Duplicates are
 * summarised in one line instead of being listed as separate cards.
 */
@Composable
private fun GroupCard(group: IncidentGroup, origin: LatLon?, onClick: () -> Unit, bordered: Boolean = true) {
    val incident = group.primary
    val sentAt = incident.submittedAt?.toDate()?.time
    val flaggedBorder = if (group.isFlagged) BorderStroke(2.dp, Amber) else BorderStroke(1.dp, Border)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (bordered || group.isFlagged) flaggedBorder else null
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
                StatusChip(incident.statusEnum())
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

            if (group.isFlagged) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Flagged for review", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (group.duplicates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val flagged = group.flaggedDuplicates
                Text(
                    text = buildString {
                        append("+${group.duplicates.size} duplicate report")
                        if (group.duplicates.size != 1) append("s")
                        if (flagged > 0) append(" ($flagged flagged)")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val time = sentAt?.let { timeAgoLabel(it, System.currentTimeMillis()) } ?: ChatRules.SENDING_LABEL
                val away = origin?.let {
                    DashboardRules.distanceLabel(DashboardRules.metersFrom(it.latitude, it.longitude, group))
                }
                Text(
                    text = if (away != null) "$time · $away away" else time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SeverityChip(incident.severityEnum())
            }
        }
    }
}

// ---------- Previews ----------

private fun sample(
    id: String,
    type: FireType,
    status: IncidentStatus,
    severity: Severity,
    flagged: Boolean = false,
    duplicates: Int = 0
) = IncidentGroup(
    primary = Incident(
        id = id, fireType = type.value, status = status.value, severity = severity.value,
        addressText = "123 Rizal St., Purok 3, Bagumbayan", isSuspicious = flagged,
        latitude = 14.5995, longitude = 120.9842
    ),
    duplicates = List(duplicates) { Incident(id = "$id-d$it", duplicateOf = id, isDuplicate = true) }
)

private val previewGroups = listOf(
    sample("1", FireType.STRUCTURAL, IncidentStatus.ON_SCENE, Severity.CRITICAL, duplicates = 2),
    sample("2", FireType.ELECTRICAL, IncidentStatus.REPORTED, Severity.HIGH, flagged = true),
    sample("3", FireType.RUBBISH, IncidentStatus.VERIFIED, Severity.LOW)
)

@Composable
private fun PreviewScreen(state: DashboardUiState) {
    FIRESTheme {
        DashboardContent(
            state = state,
            onTab = {}, onStatusFilter = {}, onDistanceFilter = {}, onSelect = {}, onClearSelection = {},
            onRecenter = {}, onRetry = {}, onOpenIncident = {}, onHistory = {}, onSignOut = {}
        )
    }
}

private val previewCounts = mapOf(
    StatusFilter.ACTIVE to 3, StatusFilter.REPORTED to 1, StatusFilter.VERIFIED to 1,
    StatusFilter.DISPATCHED to 0, StatusFilter.ON_SCENE to 1
)

@Preview(name = "Dashboard - list", showSystemUi = true)
@Composable
private fun DashboardListPreview() = PreviewScreen(
    DashboardUiState(
        groups = previewGroups, counts = previewCounts, total = 3,
        tab = DashboardTab.LIST, origin = LatLon(14.6000, 120.9800), isLoading = false
    )
)

@Preview(name = "Dashboard - map", showSystemUi = true)
@Composable
private fun DashboardMapPreview() = PreviewScreen(
    DashboardUiState(groups = previewGroups, counts = previewCounts, total = 3, isLoading = false)
)

@Preview(name = "Dashboard - nothing matches", showSystemUi = true)
@Composable
private fun DashboardNoMatchPreview() = PreviewScreen(
    DashboardUiState(
        counts = previewCounts, total = 3, tab = DashboardTab.LIST,
        statusFilter = StatusFilter.DISPATCHED, isLoading = false
    )
)

@Preview(name = "Dashboard - error", showSystemUi = true)
@Composable
private fun DashboardErrorPreview() = PreviewScreen(
    DashboardUiState(isLoading = false, error = DashboardViewModel.LOAD_ERROR)
)
