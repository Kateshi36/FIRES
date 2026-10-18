package com.example.fires.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Makes dots glide instead of jumping (H4). A responder's position arrives every 10 to 30
 * seconds; drawn as-is the dot would teleport. This returns, for each id, a position that slides
 * from where the dot is drawn right now to the newest one over [durationMillis].
 *
 * If a new position arrives in the middle of a glide, the new glide starts from where the dot
 * currently is, so it never snaps back. An id seen for the first time simply appears at its spot.
 */
@Composable
fun rememberSmoothPositions(
    targets: Map<String, LatLon>,
    durationMillis: Int = 1_500
): Map<String, LatLon> {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(targets) }
    var to by remember { mutableStateOf(targets) }

    LaunchedEffect(targets) {
        val shownNow = blend(from, to, progress.value)
        progress.snapTo(0f)
        from = targets.mapValues { (id, latest) -> shownNow[id] ?: latest }
        to = targets
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    }
    return blend(from, to, progress.value)
}

/** Each id in [to], [t] of the way (0 to 1) from its [from] position. Straight-line is exact enough over a few hundred metres. */
internal fun blend(from: Map<String, LatLon>, to: Map<String, LatLon>, t: Float): Map<String, LatLon> =
    to.mapValues { (id, end) ->
        val start = from[id] ?: return@mapValues end
        LatLon(
            latitude = start.latitude + (end.latitude - start.latitude) * t,
            longitude = start.longitude + (end.longitude - start.longitude) * t
        )
    }
