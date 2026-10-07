package com.example.fires.ui.responder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.BatteryPromptRules

/**
 * Explains why the app asks to skip battery optimization (F1.13). Shown on the first dashboard
 * visit, and again from the dashboard menu, with the default wording about alerts. At the first
 * "Start response" (H5e) the caller passes the location-sharing [title] and [message]. No logic
 * here: the screen decides what Allow and Not now do.
 */
@Composable
fun BatteryOptimizationDialog(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    title: String = BatteryPromptRules.ALERTS_TITLE,
    message: String = BatteryPromptRules.ALERTS_MESSAGE
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                // F1.14: phone makers add their own switch on top of Android's.
                Text(
                    "Some phone brands (Xiaomi, Oppo, Vivo and others) also have a separate " +
                        "\"Autostart\" or \"Battery\" setting in the phone's settings. If you " +
                        "have one of these phones, turn it on for F.I.R.E.S. too.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } }
    )
}

@Preview(name = "Battery dialog - location sharing")
@Composable
private fun BatteryLocationDialogPreview() {
    FIRESTheme {
        BatteryOptimizationDialog(
            onAllow = {},
            onNotNow = {},
            title = BatteryPromptRules.LOCATION_TITLE,
            message = BatteryPromptRules.LOCATION_MESSAGE
        )
    }
}

@Preview(name = "Battery dialog")
@Composable
private fun BatteryOptimizationDialogPreview() {
    FIRESTheme { BatteryOptimizationDialog(onAllow = {}, onNotNow = {}) }
}
