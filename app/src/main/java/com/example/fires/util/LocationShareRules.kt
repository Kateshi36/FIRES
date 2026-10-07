package com.example.fires.util

import com.example.fires.data.model.Assignment
import com.example.fires.data.model.IncidentStatus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * When a responder may share their live location, when it must stop, and how often a position is
 * written (H2). Pure Kotlin, no Android classes, so it runs as a plain JVM test.
 *
 * The flow it enforces: a responder taps "Start response" while the incident is DISPATCHED, and
 * sharing runs until the incident moves on (ON_SCENE, RESOLVED, DISMISSED) or is stepped back, or
 * the responder taps Stop. Location is shared only during an active response, never otherwise.
 */
object LocationShareRules {

    const val NOT_ASSIGNED_NOTE = "Only a responder assigned to this incident can share a live location."
    const val SHARING_NOTE =
        "Your live location is shared with staff and with the person who reported this fire " +
            "until you reach the scene or tap Stop."

    /**
     * Shown in the sharing notification and on the incident screen when the phone's GPS (its
     * location switch) goes off during a response (H5b).
     */
    const val GPS_OFF_NOTE = "GPS is off. Turn it on so the reporter can see you."

    /** The separate notification posted when sharing ends because location permission is gone. */
    const val STOPPED_TITLE = "Sharing stopped"
    const val STOPPED_PERMISSION_TEXT =
        "Location permission was removed, so your location is no longer shared. " +
            "Open the app to start sharing again."

    /**
     * Is the phone's GPS switched off while we share? Fused Location says "not available" for two
     * different reasons: the location switch is off, or there is just no fix for the moment
     * (indoors, a tunnel). Only the first is something the responder can fix, and "GPS is off"
     * would be wrong for the second, so only the first counts.
     *
     * @param isLocationAvailable what Fused Location's availability callback reported
     * @param isLocationSwitchOn  the phone's location switch right now
     */
    fun gpsLost(isLocationAvailable: Boolean, isLocationSwitchOn: Boolean): Boolean =
        !isLocationAvailable && !isLocationSwitchOn

    /** Fused Location asks for a fix this often. A moving responder is written at this pace. */
    const val INTERVAL_MS = 10_000L

    /** Moved at least this far since the last write: worth writing right away. */
    const val MIN_MOVE_METERS = 20.0

    /**
     * Standing still still writes this often, so the citizen's map can tell "not moving" from
     * "phone lost signal" (the citizen screen calls a location stale after about 60 seconds).
     */
    const val HEARTBEAT_MS = 30_000L

    /**
     * Sharing is only for a DISPATCHED incident. Anything else (on scene, resolved, dismissed, or
     * stepped back to verified) is not an active response, so sharing stops there.
     */
    fun shouldKeepSharing(status: IncidentStatus): Boolean = status == IncidentStatus.DISPATCHED

    /**
     * Who counts as "assigned": an incident's assignments hold the units and resources sent, and
     * record the responder who made each one in responderId. That responder is the one who takes
     * the response. A different responder who was not part of the assignment does not get the button.
     */
    fun isAssignedTo(uid: String?, assignments: List<Assignment>): Boolean =
        !uid.isNullOrBlank() && assignments.any { it.responderId == uid }

    fun canStart(status: IncidentStatus, assignments: List<Assignment>, uid: String?): Boolean =
        shouldKeepSharing(status) && isAssignedTo(uid, assignments)

    /**
     * Should this fix be written to Firestore? Yes for the first one, when the responder has moved
     * far enough, or when the heartbeat is due. Otherwise skip it, which keeps writes low.
     *
     * @param lastWrite the last position that was written, or null if none yet
     */
    fun shouldWrite(
        lastWrite: Fix?,
        latitude: Double,
        longitude: Double,
        nowMillis: Long
    ): Boolean {
        if (lastWrite == null) return true
        val elapsed = nowMillis - lastWrite.atMillis
        if (elapsed >= HEARTBEAT_MS) return true
        val moved = distanceMeters(lastWrite.latitude, lastWrite.longitude, latitude, longitude)
        return moved >= MIN_MOVE_METERS && elapsed >= MIN_GAP_MS
    }

    /** Two writes are never closer together than this, even when the phone reports a jump. */
    private const val MIN_GAP_MS = 5_000L

    /** A position that was written, and when. */
    data class Fix(val latitude: Double, val longitude: Double, val atMillis: Long)

    /** Straight-line distance between two points in metres (haversine). Plenty exact for 20 m. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Heading and speed from the phone are only meaningful when it says it has them. A negative
     * or NaN value means "unknown", which is stored as null.
     */
    fun cleanOrNull(value: Float, hasValue: Boolean): Double? =
        if (hasValue && !value.isNaN() && value >= 0f) value.toDouble() else null
}
