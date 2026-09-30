package com.example.fires.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fires.data.model.Severity

/*
 * Temporary screens so the whole navigation graph works today.
 * Each one is replaced by the real screen in a later phase.
 */

data class PlaceholderAction(val label: String, val onClick: () -> Unit)

@Composable
fun PlaceholderScreen(
    title: String,
    note: String,
    actions: List<PlaceholderAction> = emptyList()
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            actions.forEach { action ->
                PrimaryButton(text = action.label, onClick = action.onClick)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Placeholder that also proves the map works.
 * showPin = true  : citizen style, one draggable pin (like the report form)
 * showPin = false : responder style, colored incident markers (demo data)
 */
@Composable
fun MapDemoScreen(
    title: String,
    note: String,
    showPin: Boolean,
    actions: List<PlaceholderAction> = emptyList()
) {
    var pin by remember { mutableStateOf(DEFAULT_MAP_CENTER) }
    var selected by remember { mutableStateOf<String?>(null) }

    val demoMarkers = remember {
        val c = DEFAULT_MAP_CENTER
        listOf(
            MapPoint("demo-critical", c.latitude + 0.0020, c.longitude + 0.0020, "Critical", severityColor(Severity.CRITICAL)),
            MapPoint("demo-high", c.latitude - 0.0015, c.longitude + 0.0010, "High", severityColor(Severity.HIGH)),
            MapPoint("demo-medium", c.latitude + 0.0005, c.longitude - 0.0020, "Medium", severityColor(Severity.MEDIUM)),
            MapPoint("demo-low", c.latitude - 0.0020, c.longitude - 0.0012, "Low", severityColor(Severity.LOW))
        )
    }
    val pinHandler: (LatLon) -> Unit = { pin = it }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FiresMap(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                markers = if (showPin) emptyList() else demoMarkers,
                pin = if (showPin) pin else null,
                onPinChange = if (showPin) pinHandler else null,
                onMarkerClick = { selected = it }
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (showPin) "Pin: %.5f, %.5f".format(pin.latitude, pin.longitude)
                    else "Selected marker: ${selected ?: "none"}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                actions.forEach { action ->
                    PrimaryButton(text = action.label, onClick = action.onClick)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
