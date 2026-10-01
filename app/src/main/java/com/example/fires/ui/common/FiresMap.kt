package com.example.fires.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
private fun MapPreviewStandIn(modifier: Modifier, markers: List<MapPoint>, pin: LatLon?) {
    Box(
        modifier = modifier.background(Color(0xFFDCE8D5)),
        contentAlignment = Alignment.Center
    ) {
        val what = when {
            pin != null -> "Map preview (draggable pin)"
            markers.isNotEmpty() -> "Map preview (${markers.size} incident markers)"
            else -> "Map preview"
        }
        Text(what, color = Color(0xFF4A5A44))
    }
}

data class LatLon(val latitude: Double, val longitude: Double)

/** One incident (or anything) shown as a colored dot on the map. */
data class MapPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val color: Color = Color(0xFFE8352A),
    /** Outline color. The dashboard uses it for the status, with [color] for the severity. */
    val ringColor: Color? = null,
    /** Dashed outline and a bigger dot, to stand out. The dashboard uses it for flagged reports. */
    val highlighted: Boolean = false
)

/** Fallback map center (Manila) until we have the real GPS fix. TODO: set to Barangay Bagumbayan. */
val DEFAULT_MAP_CENTER = LatLon(14.5995, 120.9842)

/**
 * OpenStreetMap view (osmdroid). One composable for every map in the app:
 *  - [markers]        incident dots for the responder dashboard, colored by severity/status
 *  - [userLocation]   small blue dot for "you are here"
 *  - [pin]+[onPinChange]  the draggable pin in the report form. Drag it, or tap the map to move it.
 *  - [center]         the map glides there whenever it changes
 */
@Composable
fun FiresMap(
    modifier: Modifier = Modifier,
    center: LatLon = DEFAULT_MAP_CENTER,
    zoom: Double = 16.0,
    markers: List<MapPoint> = emptyList(),
    userLocation: LatLon? = null,
    pin: LatLon? = null,
    onPinChange: ((LatLon) -> Unit)? = null,
    onMarkerClick: ((String) -> Unit)? = null,
    /** Change this number to make the map glide back to [center], even if [center] itself did not change. */
    recenterKey: Int = 0
) {
    // Android Studio's preview cannot run osmdroid, so draw a stand-in there. The real map is unchanged.
    if (LocalInspectionMode.current) {
        MapPreviewStandIn(modifier, markers, pin)
        return
    }

    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(center.latitude, center.longitude))
        }
    }

    // osmdroid needs to know when the screen is shown or hidden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(center, recenterKey) {
        mapView.controller.animateTo(GeoPoint(center.latitude, center.longitude))
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            // Simple approach: rebuild all overlays whenever the inputs change.
            map.overlays.clear()

            if (onPinChange != null) {
                map.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onPinChange(LatLon(p.latitude, p.longitude))
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    })
                )
            }

            markers.forEach { point ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(point.latitude, point.longitude)
                        title = point.title
                        icon = circleIcon(
                            context,
                            point.color.toArgb(),
                            sizeDp = if (point.highlighted) 36 else 26,
                            strokeColor = point.ringColor?.toArgb() ?: android.graphics.Color.WHITE,
                            strokeDp = if (point.ringColor != null) 5 else 3,
                            dashed = point.highlighted
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick?.invoke(point.id)
                            true // handled: don't open the default info bubble
                        }
                    }
                )
            }

            userLocation?.let { me ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(me.latitude, me.longitude)
                        icon = circleIcon(context, 0xFF1A73E8.toInt(), sizeDp = 16)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                )
            }

            pin?.let { p ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(p.latitude, p.longitude)
                        icon = circleIcon(context, 0xFFE8352A.toInt(), sizeDp = 34, strokeColor = 0xFF1F2124.toInt())
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isDraggable = true
                        setOnMarkerClickListener { _, _ -> true }
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) {}
                            override fun onMarkerDragStart(marker: Marker) {}
                            override fun onMarkerDragEnd(marker: Marker) {
                                onPinChange?.invoke(
                                    LatLon(marker.position.latitude, marker.position.longitude)
                                )
                            }
                        })
                    }
                )
            }

            map.invalidate()
        }
    )
}

/** A colored circle with a border, drawn in code so we need no image files. */
private fun circleIcon(
    context: Context,
    color: Int,
    sizeDp: Int,
    strokeColor: Int = android.graphics.Color.WHITE,
    strokeDp: Int = 3,
    dashed: Boolean = false
): Drawable {
    val density = context.resources.displayMetrics.density
    val px = (sizeDp * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (dashed) {
            setStroke((strokeDp * density).toInt(), strokeColor, 7 * density, 4 * density)
        } else {
            setStroke((strokeDp * density).toInt(), strokeColor)
        }
        setSize(px, px)
        setBounds(0, 0, px, px)
    }
}
