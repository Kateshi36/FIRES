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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fires.ui.theme.Amber
import com.example.fires.ui.theme.Blue
import com.example.fires.ui.theme.FireRedDark
import com.example.fires.ui.theme.FireRedSoft
import com.example.fires.ui.theme.Green

/*
 * Small building blocks of the round 2 design (the "App round 2" boards): tone pills, white cards,
 * the round back button, banners and the uppercase section label. Every colour comes from the
 * theme, so light and dark mode both work.
 */

enum class Tone { GREEN, AMBER, BLUE, GREY, RED }

/** Background and text colour of a tone. */
@Composable
fun toneBackground(tone: Tone): Color = when (tone) {
    Tone.GREEN -> Green.copy(alpha = 0.16f)
    Tone.AMBER -> Amber.copy(alpha = 0.16f)
    Tone.BLUE -> Blue.copy(alpha = 0.16f)
    Tone.GREY -> MaterialTheme.colorScheme.surfaceVariant
    Tone.RED -> FireRedSoft
}

@Composable
fun toneContent(tone: Tone): Color = when (tone) {
    Tone.GREEN -> Green
    Tone.AMBER -> Amber
    Tone.BLUE -> Blue
    Tone.GREY -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Tone.RED -> FireRedDark
}

/** A rounded label such as "Resolved" or "Awaiting admin". The icon is optional. */
@Composable
fun TonePill(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = toneBackground(tone)) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = toneContent(tone), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                color = toneContent(tone),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/** The white rounded card used all over the round 2 screens. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        content = content
    )
}

/** The 48 dp round back button of the round 2 headers. */
@Composable
fun RoundBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}

/**
 * Round 2 header: optional round back button, a small grey line above, and a 24 sp title.
 * [trailing] is drawn at the right end (an avatar or a bell).
 */
@Composable
fun RoundHeader(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) RoundBackButton(onBack)
        Column(modifier = Modifier.weight(1f)) {
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
        trailing()
    }
}

/** "THIS MONTH", "MY DETAILS": the small uppercase label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A rounded box with an icon and a sentence: the blue "waiting" note, the amber hint, the green "on the way". */
@Composable
fun ToneBanner(
    text: String,
    tone: Tone,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = toneBackground(tone)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = toneContent(tone), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = toneContent(tone)
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (title == null) FontWeight.SemiBold else FontWeight.Normal,
                    color = toneContent(tone)
                )
            }
        }
    }
}

/** A thin rounded bar split in equal segments; the first [filled] are in the brand colour. */
@Composable
fun SegmentBar(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        if (index < filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}
