package com.example.fires.data.model

import com.example.fires.ui.common.LatLon

/**
 * A driving route from the routing service (OSRM). NOT a Firestore model: it lives in memory only.
 * Only the distance and the ETA are written to Firestore (see ResponderLocation).
 */
data class Route(
    /** The road line from the responder to the incident, in order. Drawn on the map. */
    val points: List<LatLon>,
    val distanceMeters: Int,
    val durationSeconds: Int,
    /** Turn-by-turn steps, in order. The last one is the arrival. */
    val steps: List<RouteStep> = emptyList(),
    /**
     * True for the straight-line fallback (H5d): two points and the distance between them, drawn
     * when no road route could be fetched. [durationSeconds] is 0 and means "unknown", so an
     * estimate has no ETA and must never be shown or written as one.
     */
    val isEstimate: Boolean = false
)

/** One instruction, for example "Turn left onto Rizal St." and how far it runs. */
data class RouteStep(val instruction: String, val distanceMeters: Int)
