package com.example.fires.ui.common

import android.content.Context
import android.graphics.DashPathEffect
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
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
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
 *  - [route]          a road line (the responder's route), drawn under the dots
 *
 * Every map shows the "© OpenStreetMap contributors" credit in its bottom-left corner.
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
    recenterKey: Int = 0,
    /** A road line from the responder to the incident. Empty means no line. */
    route: List<LatLon> = emptyList(),
    /** Draw [route] as a dashed line: it is only a straight-line estimate, not a road route (H5d). */
    routeDashed: Boolean = false,
    /**
     * When this changes to a non-null value, the map zooms out once to fit [route]. Use something
     * that stays the same while the route is refreshed (the incident id), so the map does not
     * jump back while the responder is looking around.
     */
    fitRouteKey: Any? = null,
    /**
     * What to fit when [fitRouteKey] changes. Empty means fit [route]. The citizen's tracker passes
     * the fire and the responder here, since it has no route line to draw.
     */
    fitPoints: List<LatLon> = emptyList()
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

    // The OpenStreetMap credit, "© OpenStreetMap contributors" (the data license, ODbL, requires it
    // wherever the map is shown). osmdroid takes the wording from the tile source, so it stays right
    // if the source changes. Every map in the app is this composable, so it is added here, once.
    val credit = remember { CopyrightOverlay(context) }

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

    LaunchedEffect(fitRouteKey) {
        val points = if (fitPoints.size >= 2) fitPoints else route
        if (fitRouteKey != null && points.size >= 2) {
            val box = BoundingBox.fromGeoPoints(points.map { GeoPoint(it.latitude, it.longitude) })
            // Two points almost on top of each other (the responder has arrived) would zoom to the
            // closest level; leave the map where it is instead.
            val tiny = box.latitudeSpan < 0.0002 && box.longitudeSpan < 0.0002
            // post {}: the map must have its size before it can fit a box.
            if (!tiny) mapView.post { mapView.zoomToBoundingBox(box, true, 100) }
        }
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

            if (route.size >= 2) {
                map.overlays.add(
                    Polyline(map).apply {
                        setPoints(route.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.color = 0xFF1A73E8.toInt()
                        outlinePaint.strokeWidth = 12f
                        if (routeDashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(36f, 24f), 0f)
                    }
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

            // Last, so it draws on top of everything. It has to be added again on every update,
            // because overlays.clear() above removes it. It ignores touches, so the pin and the
            // marker taps still work underneath.
            map.overlays.add(credit)

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