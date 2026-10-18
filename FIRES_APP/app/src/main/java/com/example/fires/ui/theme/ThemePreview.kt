package com.example.fires.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Design-time previews of the palette and text styles from the design sheet.

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, Gray300, RoundedCornerShape(8.dp))
        )
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Colors", showBackground = true, widthDp = 360)
@Composable
private fun ColorsPreview() {
    FIRESTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("FireRed", FireRed)
                Swatch("FireRedDark", FireRedDark)
                Swatch("FireRedSoft", FireRedSoft)
                Swatch("Ink", Ink)
                Swatch("Gray600", Gray600)
                Swatch("Gray300", Gray300)
                Swatch("Canvas", Canvas)
                Swatch("Border", Border)
                Swatch("Amber", Amber)
                Swatch("Green", Green)
                Swatch("Blue", Blue)
            }
        }
    }
}

@Preview(name = "Typography", showBackground = true, widthDp = 360)
@Composable
private fun TypographyPreview() {
    FIRESTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Headline medium", style = MaterialTheme.typography.headlineMedium)
                Text("Headline small", style = MaterialTheme.typography.headlineSmall)
                Text("Title large", style = MaterialTheme.typography.titleLarge)
                Text("Title medium", style = MaterialTheme.typography.titleMedium)
                Text("Body large - the quick brown fox", style = MaterialTheme.typography.bodyLarge)
                Text("Body medium - the quick brown fox", style = MaterialTheme.typography.bodyMedium)
                Text("Label large", style = MaterialTheme.typography.labelLarge)
                Text("Label medium", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
