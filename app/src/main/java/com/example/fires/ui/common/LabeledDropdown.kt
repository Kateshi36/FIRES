package com.example.fires.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A pick-one-from-a-list field that looks like [LabeledTextField] (label above, outlined box).
 *
 * Built from a read-only text field with a tappable layer on top and a DropdownMenu, so it does not
 * depend on the experimental ExposedDropdownMenuBox API, which changed between Compose versions.
 *
 * @param value the current choice. Shown as-is, even if it is not in [options] (for example an
 *              older free-text value), so nothing the person saved earlier disappears.
 * @param noSelectionLabel when set, adds a first menu item with this text that clears the choice
 *              (calls [onOptionSelected] with ""). Use it for optional fields.
 */
@Composable
fun LabeledDropdown(
    label: String,
    value: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    noSelectionLabel: String? = null,
    error: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { fieldWidthPx = it.width }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {}, // read-only: the value only changes through the menu
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                        contentDescription = null
                    )
                },
                supportingText = if (error != null) {
                    { Text(error) }
                } else null
            )

            // A text field swallows taps to open the keyboard, so a see-through layer on top
            // catches the tap and opens the menu instead.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // Same width as the field, so the list lines up under it.
                modifier = Modifier.width(with(density) { fieldWidthPx.toDp() })
            ) {
                if (noSelectionLabel != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = noSelectionLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            onOptionSelected("")
                            expanded = false
                        }
                    )
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
