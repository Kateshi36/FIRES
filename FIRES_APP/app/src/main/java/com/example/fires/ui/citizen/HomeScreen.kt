package com.example.fires.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.ui.common.DEFAULT_MAP_CENTER
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.LocationAccess
import com.example.fires.ui.common.LocationBanner
import com.example.fires.ui.common.rememberLocationAccess
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.LocationGate
import com.example.fires.viewmodel.HomeUiState
import com.example.fires.viewmodel.HomeViewModel

/**
 * Citizen Home / SOS (D1): the live map with your position and the big SOS button.
 * The SOS button opens the report form. It works even when location is off.
 */
@Composable
fun HomeScreen(
    onSosClick: () -> Unit,
    onMyReportsClick: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val location = rememberLocationAccess()

    // Read the GPS as soon as permission + GPS switch are both fine (also after coming back from Settings).
    val gpsReady = location.gate is LocationGate.Ready
    LaunchedEffect(gpsReady) {
        if (gpsReady) viewModel.refreshLocation(context)
    }

    HomeContent(
        state = state,
        location = location,
        onRecenter = { viewModel.refreshLocation(context) },
        onSosClick = onSosClick,
        onMyReportsClick = onMyReportsClick,
        onSignOut = onSignOut
    )
}

/** The look of the Home screen. No ViewModel here, so it can be previewed. */
@Composable
fun HomeContent(
    state: HomeUiState,
    location: LocationAccess,
    onRecenter: () -> Unit,
    onSosClick: () -> Unit,
    onMyReportsClick: () -> Unit,
    onSignOut: () -> Unit
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
                Text(
                    text = "F.I.R.E.S.",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("My reports") },
                            onClick = {
                                menuOpen = false
                                onMyReportsClick()
                            }
                        )
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

            // ---- Location notice (only when something needs fixing) ----
            if (location.gate !is LocationGate.Ready) {
                LocationBanner(
                    location = location,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ---- "Live map" label ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22A559)))
                Spacer(Modifier.width(8.dp))
                Text("Live map", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                val status = when {
                    state.position != null -> "Showing your location"
                    state.isLocating -> "Finding your location…"
                    else -> ""
                }
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Map ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                FiresMap(
                    modifier = Modifier.fillMaxSize(),
                    center = state.position ?: DEFAULT_MAP_CENTER,
                    userLocation = state.position,
                    recenterKey = state.recenterKey
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
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
            }

            // ---- SOS ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SosButton(onClick = onSosClick)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap to report",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You will need to log in again to report a fire.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }
}

/** The big red round button. Opens the report form. */
@Composable
private fun SosButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(148.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = "SOS. Report a fire." }
            .clickable(onClickLabel = "Report a fire", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SOS",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------- Previews ----------

private val previewPosition = LatLon(14.5995, 120.9842)
private fun ready() = LocationAccess(LocationGate.Ready, {}, {}, {}, {})

@Preview(name = "Home - GPS ready", showSystemUi = true)
@Composable
private fun HomeReadyPreview() {
    FIRESTheme {
        HomeContent(HomeUiState(position = previewPosition), ready(), {}, {}, {}, {})
    }
}

@Preview(name = "Home - finding location", showSystemUi = true)
@Composable
private fun HomeLocatingPreview() {
    FIRESTheme {
        HomeContent(HomeUiState(isLocating = true), ready(), {}, {}, {}, {})
    }
}

@Preview(name = "Home - GPS off", showSystemUi = true)
@Composable
private fun HomeGpsOffPreview() {
    FIRESTheme {
        HomeContent(HomeUiState(), LocationAccess(LocationGate.GpsOff, {}, {}, {}, {}), {}, {}, {}, {})
    }
}

@Preview(name = "Home - permission denied", showSystemUi = true)
@Composable
private fun HomeDeniedPreview() {
    FIRESTheme {
        HomeContent(
            HomeUiState(),
            LocationAccess(LocationGate.PermissionDenied(canAskAgain = false), {}, {}, {}, {}),
            {}, {}, {}, {}
        )
    }
}
