package com.example.fires.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fires.ui.theme.FIRESTheme

/** A tap-to-toggle chip (several can be on at once). Used for vulnerable persons and hazards. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onToggle() },
        shape = shape,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outline)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Chips laid out in a grid with [columns] per row. Equal widths, equal heights in a row. */
@Composable
fun <T> ChipGrid(
    items: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    ChoiceChip(
                        text = label(item),
                        selected = isSelected(item),
                        onToggle = { onToggle(item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                // Keep the last row's chips the same width as the rows above it.
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One option of a pick-one list (radio button inside a bordered row). */
@Composable
fun RadioChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(12.dp)
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = shape,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outline)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface
            )
        }
    }
}

/** A number with minus and plus buttons. */
@Composable
fun CountStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    unitLabel: String = "people"
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValueChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Filled.Remove, contentDescription = "Fewer $unitLabel")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        IconButton(onClick = { onValueChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = "More $unitLabel")
        }
    }
}

@Preview(name = "Choice controls", showBackground = true, widthDp = 360)
@Composable
private fun ChoiceControlsPreview() {
    FIRESTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChipGrid(
                items = listOf("Children", "Elderly", "Pregnant", "Person with disability"),
                isSelected = { it == "Elderly" },
                label = { it },
                onToggle = {}
            )
            RadioChoiceRow("Small (contained)", selected = false, onClick = {})
            RadioChoiceRow("Medium (spreading)", selected = true, onClick = {})
            CountStepper(value = 3, onValueChange = {}, min = 0, max = 99)
        }
    }
}
