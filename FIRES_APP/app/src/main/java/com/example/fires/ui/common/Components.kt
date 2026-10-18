package com.example.fires.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.ui.auth.SplashContent
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.Blue
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.FireRed
import com.example.fires.ui.theme.FireRedDark
import com.example.fires.ui.theme.Gray600
import com.example.fires.ui.theme.Green
import com.example.fires.viewmodel.SessionState

// ---------- Colors that depend on data ----------

fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> FireRedDark
    Severity.HIGH -> Amber
    Severity.MEDIUM -> Blue
    Severity.LOW -> Green
}

fun statusColor(status: IncidentStatus): Color = when (status) {
    IncidentStatus.REPORTED -> Amber
    IncidentStatus.VERIFIED -> Blue
    IncidentStatus.DISPATCHED -> FireRed
    IncidentStatus.ON_SCENE -> FireRedDark
    IncidentStatus.RESOLVED -> Green
    IncidentStatus.DISMISSED -> Gray600
}

// ---------- Buttons ----------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Text field ----------

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            shape = RoundedCornerShape(12.dp),
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType
            ),
            visualTransformation = if (isPassword && !showPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                }
            } else null,
            supportingText = if (error != null) {
                { Text(error) }
            } else null
        )
    }
}

// ---------- Chips ----------

@Composable
fun ChipPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SeverityChip(severity: Severity, modifier: Modifier = Modifier) =
    ChipPill(severity.label, severityColor(severity), modifier)

@Composable
fun StatusChip(status: IncidentStatus, modifier: Modifier = Modifier) =
    ChipPill(status.label, statusColor(status), modifier)

// ---------- Status stepper (citizen "Report status" screen) ----------

@Composable
fun StatusStepper(status: IncidentStatus, modifier: Modifier = Modifier) {
    val steps = listOf("Received", "Verified", "Dispatched", "On scene")
    // Which step is "current". RESOLVED (4) means every step is done. DISMISSED (-1) means none.
    val currentIndex = when (status) {
        IncidentStatus.REPORTED -> 0
        IncidentStatus.VERIFIED -> 1
        IncidentStatus.DISPATCHED -> 2
        IncidentStatus.ON_SCENE -> 3
        IncidentStatus.RESOLVED -> 4
        IncidentStatus.DISMISSED -> -1
    }
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        steps.forEachIndexed { index, label ->
            val done = index < currentIndex
            val current = index == currentIndex

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (done || current) primary else idle),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        done -> Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        current -> Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (done || current) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(if (index < currentIndex) primary else idle)
                )
            }
        }
        if (status == IncidentStatus.RESOLVED || status == IncidentStatus.DISMISSED) {
            Spacer(Modifier.height(12.dp))
            StatusChip(status)
        }
    }
}
