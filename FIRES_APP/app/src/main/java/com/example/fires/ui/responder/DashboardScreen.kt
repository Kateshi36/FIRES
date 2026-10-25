package com.example.fires.ui.responder

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.Verification
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AssignedNotice
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.DEFAULT_MAP_CENTER
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.MapPoint
import com.example.fires.ui.common.RoundHeader
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SoftCard
import com.example.fires.ui.common.StatusChip
import com.example.fires.ui.common.Tone
import com.example.fires.ui.common.TonePill
import com.example.fires.ui.common.severityColor
import com.example.fires.ui.common.statusColor
import com.example.fires.ui.common.statusRingColor
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.AssignedNoticeRules
import com.example.fires.util.BatteryOptimization
import com.example.fires.util.BatteryPromptRules
import com.example.fires.util.ChatRules
import com.example.fires.util.DashboardTab
import com.example.fires.util.DashboardTiles
import com.example.fires.util.IncidentGroup
import com.example.fires.util.ResponderDashboardRules
import com.example.fires.util.incidentCode
import com.example.fires.util.timeAgoLabel
import com.example.fires.viewmodel.DashboardUiState
import com.example.fires.viewmodel.DashboardViewModel

/** The black "You were assigned" banner keeps the same dark look in light and dark mode. */
private val BannerInk = Color(0xFF1B1A19)

/**
 * Responder dashboard (round 2 design): a greeting with the notice bell, the black "You were
 * assigned" banner, the Open / Awaiting admin / Mine counts and three segments: Mine, All open
 * and Map. Both lists and the map show the same live data.
 *
 * @param onOpenIncident opens the detail screen of an incident (gets the incident id).
 */
@Composable
fun DashboardScreen(
    onOpenIncident: (String) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadSeen(context)
        viewModel.refreshLocation(context)
    }
    // Assignments are read again every time the screen comes back to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // ---- Battery optimization (F1.13): asked once on the first visit; the Profile tab has the switch ----
    // rememberSaveable: turning the phone must not close the dialog before it has been answered.
    var showBatteryDialog by rememberSaveable { mutableStateOf(false) }
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
        onSelect = viewModel::select,
        onClearSelection = viewModel::clearSelection,
        onRecenter = { viewModel.refreshLocation(context) },
        onRetry = viewModel::retry,
        onOpenIncident = { id ->
            state.banner?.let { if (it.incidentId == id) viewModel.dismissBanner(it.id) }
            onOpenIncident(id)
        },
        onDismissBanner = viewModel::dismissBanner,
        onBellOpened = { viewModel.markNoticesSeen(context) },
        onHistory = onHistory,
        onProfile = onProfile
    )
}

/** The look of the dashboard. No ViewModel here, so it can be previewed. */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onTab: (DashboardTab) -> Unit,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRecenter: () -> Unit,
    onRetry: () -> Unit,
    onOpenIncident: (String) -> Unit,
    onDismissBanner: (String) -> Unit = {},
    onBellOpened: () -> Unit = {},
    onHistory: () -> Unit,
    onProfile: () -> Unit
) {
    var bellOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {

                // ---- Greeting and bell ----
                RoundHeader(
                    title = if (state.firstName.isBlank()) "Hi, responder" else "Hi, ${state.firstName}",
                    sub = "Barangay Bagumbayan",
                    trailing = {
                        BellButton(unread = state.hasUnread, onClick = { bellOpen = true; onBellOpened() })
                    }
                )

                // ---- "You were assigned" ----
                val banner = state.banner
                if (banner != null) {
                    AssignedBanner(
                        notice = banner,
                        onOpen = { onOpenIncident(banner.incidentId) },
                        onDismiss = { onDismissBanner(banner.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                // ---- Counts ----
                Tiles(state.tiles, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

                // ---- Mine / All open / Map ----
                Segments(
                    selected = state.tab,
                    onSelect = onTab,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                Spacer(Modifier.height(4.dp))

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

            ResponderBottomBar(
                selected = ResponderTab.DASHBOARD,
                onDashboard = {},
                onHistory = onHistory,
                onProfile = onProfile
            )
        }
    }

    if (bellOpen) {
        NoticesDialog(
            notices = state.notices,
            onOpen = { id -> bellOpen = false; onOpenIncident(id) },
            onClose = { bellOpen = false }
        )
    }
}

// ---------- Header pieces ----------

@Composable
private fun BellButton(unread: Boolean, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notices")
            }
        }
        if (unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
private fun AssignedBanner(notice: AssignedNotice, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BannerInk
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "You were assigned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    AssignedNoticeRules.text(notice.text, notice.removed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open report",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun Tiles(tiles: DashboardTiles, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(tiles.open, "Open", Modifier.weight(1f))
        Tile(tiles.awaitingAdmin, "Awaiting admin", Modifier.weight(1f))
        Tile(tiles.mine, "Mine", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(number: Int, label: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Segments(selected: DashboardTab, onSelect: (DashboardTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardTab.entries.forEach { tab ->
            val on = tab == selected
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (on) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (on) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticesDialog(notices: List<AssignedNotice>, onOpen: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Notices") },
        text = {
            if (notices.isEmpty()) {
                Text("Nothing yet. You will see it here when an administrator assigns you to a report.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    notices.take(8).forEach { n ->
                        Surface(
                            onClick = { onOpen(n.incidentId) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    AssignedNoticeRules.title(n.removed),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    AssignedNoticeRules.text(n.text, n.removed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                n.createdAtMs?.let {
                                    Text(
                                        timeAgoLabel(it, System.currentTimeMillis()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
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
                EmptyNotice("No active incidents right now.")
            }
            val selected = state.selected
            if (selected != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        IncidentCard(selected, mine = selected.id in state.mineIds, onClick = { onOpenIncident(selected.id) }, bordered = false)
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
        ringColor = statusRingColor(status),
        highlighted = isFlagged
    )
}

/**
 * Explains the marker colors, the way the markers are drawn: the FILL is the severity and the thick
 * OUTLINE is the status. Each group wraps onto a new line on a narrow phone instead of squeezing the
 * words, and every label stays on one line.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Dot color = severity", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Severity.entries.reversed().forEach { LegendDot(severityColor(it), it.label, ring = false) }
            }
            Text("Outline color = status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IncidentStatus.entries.filter { it.isActive }.forEach { LegendDot(statusRingColor(it), it.label, ring = true) }
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
                .size(14.dp)
                .clip(CircleShape)
                .then(
                    if (ring) Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(3.dp, color, CircleShape)
                    else Modifier
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

// ---------- List ----------

@Composable
private fun ListView(state: DashboardUiState, onOpenIncident: (String) -> Unit) {
    val shown = state.shown
    if (shown.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyNotice(
                when {
                    state.total == 0 -> "No active incidents right now."
                    else -> ResponderDashboardRules.EMPTY_MINE
                }
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shown, key = { it.id }) { group ->
            IncidentCard(group, mine = group.id in state.mineIds, onClick = { onOpenIncident(group.id) })
        }
    }
}

@Composable
private fun EmptyNotice(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun severityTone(severity: Severity): Tone = when (severity) {
    Severity.CRITICAL, Severity.HIGH -> Tone.RED
    Severity.MEDIUM -> Tone.AMBER
    Severity.LOW -> Tone.GREEN
}

/**
 * One incident group (round 2): the address as the title, "INC-code · type · time" under it and a row
 * of pills. A report assigned to this responder has a primary outline and the "ASSIGNED TO YOU" caption.
 * Duplicates are summarised as one pill instead of being listed as separate cards.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IncidentCard(group: IncidentGroup, mine: Boolean, onClick: () -> Unit, bordered: Boolean = true) {
    val incident = group.primary
    val status = incident.statusEnum()
    val severity = incident.severityEnum()
    val sentAt = incident.submittedAt?.toDate()?.time
    val time = sentAt?.let { timeAgoLabel(it, System.currentTimeMillis()) } ?: ChatRules.SENDING_LABEL
    val border = when {
        mine -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        bordered -> androidx.compose.foundation.BorderStroke(1.dp, Border)
        else -> null
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (mine) {
                Text(
                    "ASSIGNED TO YOU",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = incident.addressText.ifBlank { incident.fireTypeEnum().label },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${incidentCode(incident.id)} · ${incident.fireTypeEnum().label} · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TonePill(
                    severity.label, severityTone(severity),
                    icon = if (severity.rank >= Severity.HIGH.rank) Icons.Filled.Warning else null
                )
                when (status) {
                    IncidentStatus.REPORTED ->
                        TonePill("Awaiting admin", Tone.GREY, icon = Icons.Filled.HourglassEmpty)
                    IncidentStatus.VERIFIED -> TonePill("Verified", Tone.BLUE)
                    else -> {
                        StatusChip(status)
                        if (incident.verification == Verification.VERIFIED.value) TonePill("Verified", Tone.BLUE)
                    }
                }
                if (incident.isDuplicate) TonePill("Possible duplicate", Tone.AMBER)
                if (group.duplicates.isNotEmpty()) {
                    TonePill(
                        "+${group.duplicates.size} duplicate report" + if (group.duplicates.size != 1) "s" else "",
                        Tone.GREY
                    )
                }
                if (group.isFlagged) TonePill("Flagged", Tone.AMBER, icon = Icons.Filled.Warning)
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
    sample("1abcdef", FireType.STRUCTURAL, IncidentStatus.ON_SCENE, Severity.CRITICAL, duplicates = 2),
    sample("2abcdef", FireType.ELECTRICAL, IncidentStatus.REPORTED, Severity.HIGH, flagged = true),
    sample("3abcdef", FireType.RUBBISH, IncidentStatus.VERIFIED, Severity.LOW)
)

@Composable
private fun PreviewScreen(state: DashboardUiState) {
    FIRESTheme {
        DashboardContent(
            state = state,
            onTab = {}, onSelect = {}, onClearSelection = {},
            onRecenter = {}, onRetry = {}, onOpenIncident = {}, onHistory = {}, onProfile = {}
        )
    }
}

@Preview(name = "Dashboard - mine", showSystemUi = true)
@Composable
private fun DashboardMinePreview() = PreviewScreen(
    DashboardUiState(
        groups = previewGroups, mine = listOf(previewGroups[0]),
        tiles = DashboardTiles(3, 1, 1), firstName = "Juan",
        banner = AssignedNotice("n", "1abcdef", null, System.currentTimeMillis(), false),
        hasUnread = true, isLoading = false
    )
)

@Preview(name = "Dashboard - all open", showSystemUi = true)
@Composable
private fun DashboardAllPreview() = PreviewScreen(
    DashboardUiState(
        groups = previewGroups, tiles = DashboardTiles(3, 1, 0), tab = DashboardTab.ALL,
        origin = LatLon(14.6000, 120.9800), isLoading = false
    )
)

@Preview(name = "Dashboard - map", showSystemUi = true)
@Composable
private fun DashboardMapPreview() = PreviewScreen(
    DashboardUiState(groups = previewGroups, tiles = DashboardTiles(3, 1, 0), tab = DashboardTab.MAP, isLoading = false)
)

@Preview(name = "Dashboard - error", showSystemUi = true)
@Composable
private fun DashboardErrorPreview() = PreviewScreen(
    DashboardUiState(isLoading = false, error = DashboardViewModel.LOAD_ERROR)
)
