package com.example.fires.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.LocationGate

/**
 * Slim "location is not ready" notice with one button that fixes it. Shows nothing when
 * location is Ready. It never blocks anything: reporting always works with the manual pin.
 */
@Composable
fun LocationBanner(location: LocationAccess, modifier: Modifier = Modifier) {
    val gate = location.gate
    val message: String
    val buttonLabel: String
    val onButton: () -> Unit

    when (gate) {
        LocationGate.Ready -> return
        LocationGate.AskPermission -> {
            message = "Allow location so responders get your exact spot."
            buttonLabel = "Allow"
            onButton = location.requestPermission
        }
        is LocationGate.PermissionDenied -> {
            message = "Location is off. You can still report by placing the pin on the map."
            buttonLabel = if (gate.canAskAgain) "Allow" else "Settings"
            onButton = if (gate.canAskAgain) location.requestPermission else location.openAppSettings
        }
        LocationGate.GpsOff -> {
            message = "Your phone's GPS is off. Turn it on, or place the pin on the map."
            buttonLabel = "Turn on"
            onButton = location.turnOnGps
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onButton) {
                Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(name = "Banner - GPS off", showBackground = true, widthDp = 360)
@Composable
private fun LocationBannerGpsOffPreview() {
    FIRESTheme { LocationBanner(LocationAccess(LocationGate.GpsOff, {}, {}, {}, {})) }
}

@Preview(name = "Banner - denied for good", showBackground = true, widthDp = 360)
@Composable
private fun LocationBannerDeniedPreview() {
    FIRESTheme {
        LocationBanner(LocationAccess(LocationGate.PermissionDenied(canAskAgain = false), {}, {}, {}, {}))
    }
}
