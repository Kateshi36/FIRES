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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fires.ui.common.DEFAULT_MAP_CENTER
import com.example.fires.ui.common.FiresMap
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.AddressLookup
import com.example.fires.viewmodel.ReportViewModel
import kotlinx.coroutines.delay
import java.util.Locale

// Lets the pin position survive a screen rotation.
private val LatLonSaver = listSaver<LatLon, Double>(
    save = { listOf(it.latitude, it.longitude) },
    restore = { LatLon(it[0], it[1]) }
)

/**
 * Pin-adjust screen (D2): drag the red pin or tap the map to put it exactly on the fire.
 * Confirm hands the spot back to the report form; Cancel leaves the form unchanged.
 * It shares the report form's ViewModel.
 */
@Composable
fun PinAdjustScreen(
    onDone: () -> Unit,
    viewModel: ReportViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Start where the fire is now, else at the phone's position, else the default center.
    val start = remember { state.location ?: state.gps ?: DEFAULT_MAP_CENTER }
    var draft by rememberSaveable(stateSaver = LatLonSaver) { mutableStateOf(start) }
    var mapCenter by rememberSaveable(stateSaver = LatLonSaver) { mutableStateOf(start) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }

    // Look up the address for the pin once it stops moving (a short pause avoids a lookup per tap).
    var address by remember { mutableStateOf<String?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    LaunchedEffect(draft) {
        lookingUp = true
        address = null
        delay(500)
        address = AddressLookup.reverse(context.applicationContext, draft)
        lookingUp = false
    }

    val gps = state.gps
    PinAdjustContent(
        pin = draft,
        mapCenter = mapCenter,
        recenterKey = recenterKey,
        gps = gps,
        address = address,
        isLookingUp = lookingUp,
        onPinChange = { draft = it },
        onUseMyLocation = if (gps != null) {
            {
                draft = gps
                mapCenter = gps
                recenterKey++
            }
        } else null,
        onConfirm = {
            viewModel.confirmPin(context, draft)
            onDone()
        },
        onCancel = onDone
    )
}

/** The look of the pin screen. No ViewModel here, so it can be previewed. */
@Composable
fun PinAdjustContent(
    pin: LatLon,
    mapCenter: LatLon,
    recenterKey: Int,
    gps: LatLon?,
    address: String?,
    isLookingUp: Boolean,
    onPinChange: (LatLon) -> Unit,
    onUseMyLocation: (() -> Unit)?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Set fire location", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = "Drag the red pin, or tap the map, to put it exactly on the fire.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            ) {
                FiresMap(
                    modifier = Modifier.fillMaxSize(),
                    center = mapCenter,
                    userLocation = gps,
                    pin = pin,
                    onPinChange = onPinChange,
                    recenterKey = recenterKey
                )
                if (onUseMyLocation != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        shape = CircleShape,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        IconButton(onClick = onUseMyLocation) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Move pin to my location")
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLookingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Looking up address…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            text = address ?: "Address not available. You can type a landmark on the form.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f", pin.latitude, pin.longitude),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "Confirm location", onClick = onConfirm)
                    SecondaryButton(text = "Cancel", onClick = onCancel)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Preview(name = "Pin screen", showSystemUi = true)
@Composable
private fun PinAdjustPreview() {
    val spot = LatLon(14.5995, 120.9842)
    FIRESTheme {
        PinAdjustContent(
            pin = spot, mapCenter = spot, recenterKey = 0, gps = spot,
            address = "123 Rizal St., Manila", isLookingUp = false,
            onPinChange = {}, onUseMyLocation = {}, onConfirm = {}, onCancel = {}
        )
    }
}

@Preview(name = "Pin screen - looking up", showSystemUi = true)
@Composable
private fun PinAdjustLookingUpPreview() {
    val spot = LatLon(14.5995, 120.9842)
    FIRESTheme {
        PinAdjustContent(
            pin = spot, mapCenter = spot, recenterKey = 0, gps = null,
            address = null, isLookingUp = true,
            onPinChange = {}, onUseMyLocation = null, onConfirm = {}, onCancel = {}
        )
    }
}
