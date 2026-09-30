package com.example.fires.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.ui.theme.FIRESTheme

/*
 * Previews for the shared components and the placeholder screens.
 * Open this file in Split or Design mode to see all of them at once.
 * Previews are design-time only; they do not change how the app runs.
 */

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    FIRESTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { content() }
        }
    }
}

@Preview(name = "Buttons", showBackground = true, widthDp = 360)
@Composable
private fun ButtonsPreview() = PreviewSurface {
    PrimaryButton(text = "Send alert", onClick = {})
    PrimaryButton(text = "Loading", onClick = {}, loading = true)
    PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
    SecondaryButton(text = "Cancel", onClick = {})
}

@Preview(name = "Text fields", showBackground = true, widthDp = 360)
@Composable
private fun TextFieldsPreview() = PreviewSurface {
    LabeledTextField(label = "Email", value = "", onValueChange = {}, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
    LabeledTextField(label = "Full name", value = "Juan Dela Cruz", onValueChange = {})
    LabeledTextField(label = "Password", value = "secret123", onValueChange = {}, isPassword = true)
    LabeledTextField(label = "Email", value = "juan@", onValueChange = {}, error = "That email address doesn't look right.")
    LabeledTextField(label = "Disabled", value = "Read only", onValueChange = {}, enabled = false)
}

@Preview(name = "Chips", showBackground = true, widthDp = 360)
@Composable
private fun ChipsPreview() = PreviewSurface {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Severity.entries.forEach { SeverityChip(it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IncidentStatus.entries.take(3).forEach { StatusChip(it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IncidentStatus.entries.drop(3).forEach { StatusChip(it) }
    }
}

@Preview(name = "Status stepper", showBackground = true, widthDp = 360)
@Composable
private fun StepperPreview() = PreviewSurface {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column { StatusStepper(IncidentStatus.REPORTED) }
        Column { StatusStepper(IncidentStatus.DISPATCHED) }
        Column { StatusStepper(IncidentStatus.RESOLVED) }
    }
}

@Preview(name = "Placeholder screen", showSystemUi = true)
@Composable
private fun PlaceholderScreenPreview() {
    FIRESTheme {
        PlaceholderScreen(
            title = "Log in",
            note = "Placeholder. Phase C: email + password login.",
            actions = listOf(
                PlaceholderAction("Sign up") {},
                PlaceholderAction("Back") {}
            )
        )
    }
}

@Preview(name = "Map screen - citizen (pin)", showSystemUi = true)
@Composable
private fun MapDemoCitizenPreview() {
    FIRESTheme {
        MapDemoScreen(
            title = "Citizen home",
            note = "Drag the red pin or tap the map to move it.",
            showPin = true,
            actions = listOf(PlaceholderAction("Report a fire") {})
        )
    }
}

@Preview(name = "Map screen - responder (markers)", showSystemUi = true)
@Composable
private fun MapDemoResponderPreview() {
    FIRESTheme {
        MapDemoScreen(
            title = "Responder dashboard",
            note = "Demo markers colored by severity.",
            showPin = false,
            actions = listOf(PlaceholderAction("History") {})
        )
    }
}
