package com.example.fires.util

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The rules and wording around a responder's route (H3). Pure Kotlin, no Android classes, so it
 * runs as a plain JVM test.
 */
object RouteRules {

    /** Ask for a new route when the responder has moved this far since the last one... */
    const val REFRESH_MOVE_METERS = 200.0

    /** ...or when the last one is this old. The public routing server is for light use. */
    const val REFRESH_INTERVAL_MS = 30_000L

    /** Never ask twice within this time, however far the phone jumped. */
    private const val MIN_GAP_MS = 10_000L

    /** Closer than this, the bar says "Arriving" instead of a distance. */
    const val ARRIVING_METERS = 50

    const val FINDING_ROUTE = "Finding the best route…"

    /**
     * A real route younger than this still counts as "recent": when a refresh fails, the responder
     * keeps seeing it instead of dropping to a straight line.
     */
    const val REAL_ROUTE_RECENT_MS = 120_000L

    /** Where a route was last asked from, and when. */
    data class RouteFix(val latitude: Double, val longitude: Double, val atMillis: Long)

    /** True for the first route, after about 200 m of movement, or after about 30 seconds. */
    fun shouldRefresh(last: RouteFix?, latitude: Double, longitude: Double, nowMillis: Long): Boolean {
        if (last == null) return true
        val elapsed = nowMillis - last.atMillis
        if (elapsed < MIN_GAP_MS) return false
        if (elapsed >= REFRESH_INTERVAL_MS) return true
        return LocationShareRules.distanceMeters(last.latitude, last.longitude, latitude, longitude) >=
            REFRESH_MOVE_METERS
    }

    // ---------- Straight-line fallback (H5d) ----------

    /**
     * The fallback when no road route can be fetched: a line from [from] to [to] and its length.
     * It is an estimate: there is no ETA (duration is 0 = unknown) and no turn-by-turn steps.
     */
    fun straightLine(from: LatLon, to: LatLon): Route = Route(
        points = listOf(from, to),
        distanceMeters = LocationShareRules.distanceMeters(
            from.latitude, from.longitude, to.latitude, to.longitude
        ).roundToInt(),
        durationSeconds = 0,
        steps = emptyList(),
        isEstimate = true
    )

    /**
     * Should a failed route fetch be answered with a straight line? Yes when there is no route at
     * all, when the current one is already a straight line (it is redrawn from where the responder
     * is now), or when the last real route is older than [REAL_ROUTE_RECENT_MS]. A recent real
     * route is kept. A real route fetched later replaces a straight line, because the service just
     * stores whatever it fetched last.
     */
    fun needsStraightLine(current: Route?, computedAtMillis: Long?, nowMillis: Long): Boolean {
        if (current == null || current.isEstimate || computedAtMillis == null) return true
        return nowMillis - computedAtMillis > REAL_ROUTE_RECENT_MS
    }

    /** What goes into Firestore for the citizen: the ETA of a REAL route, never of an estimate. */
    fun etaSecondsToWrite(route: Route?): Int? =
        route?.takeIf { !it.isEstimate }?.durationSeconds

    /** Same rule for the road distance. A straight line writes nothing, so the citizen sees no ETA. */
    fun distanceMetersToWrite(route: Route?): Int? =
        route?.takeIf { !it.isEstimate }?.distanceMeters

    // ---------- Wording ----------

    /** "850 m", "2.4 km", "12 km". */
    fun distanceLabel(meters: Int): String = when {
        meters < 995 -> "${((meters + 5) / 10) * 10} m" // 995+ would round up to "1000 m"
        meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
        else -> "${(meters + 500) / 1000} km"
    }

    /** "less than 1 min", "about 6 min", "about 1 hr 5 min". */
    fun etaLabel(seconds: Int): String {
        if (seconds < 60) return "less than 1 min"
        val minutes = (seconds + 30) / 60
        if (minutes < 60) return "about $minutes min"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "about $hours hr" else "about $hours hr $rest min"
    }

    /** The bar text: "2.4 km, about 6 min". */
    fun summary(distanceMeters: Int, durationSeconds: Int): String =
        if (distanceMeters <= ARRIVING_METERS) "Arriving"
        else distanceLabel(distanceMeters) + ", " + etaLabel(durationSeconds)

    /** "About 1.2 km, straight line (no route available)", or "Arriving" when the scene is close. */
    fun straightLineSummary(distanceMeters: Int): String =
        if (distanceMeters <= ARRIVING_METERS) "Arriving"
        else "About " + distanceLabel(distanceMeters) + ", straight line (no route available)"

    /** The bar text for either kind of route. */
    fun routeSummary(route: Route): String =
        if (route.isEstimate) straightLineSummary(route.distanceMeters)
        else summary(route.distanceMeters, route.durationSeconds)

    /**
     * One turn as a sentence, from OSRM's maneuver. [type] and [modifier] are OSRM's own words
     * ("turn" + "left", "roundabout", "arrive"...). [name] is the road, blank when it has none.
     */
    fun instruction(type: String, modifier: String?, name: String?, exit: Int? = null): String {
        val road = name?.trim().orEmpty()
        val onto = if (road.isEmpty()) "" else " onto $road"
        return when (type) {
            "depart" -> if (road.isEmpty()) "Head out" else "Head out on $road"
            "arrive" -> when (modifier) {
                "left" -> "Arrive at the scene, on your left"
                "right" -> "Arrive at the scene, on your right"
                else -> "Arrive at the scene"
            }
            "roundabout", "rotary", "roundabout turn" ->
                if (exit != null) "At the roundabout, take exit $exit$onto" else "Enter the roundabout$onto"
            "exit roundabout", "exit rotary" -> "Leave the roundabout$onto"
            "merge" -> "Merge$onto"
            "fork" -> when (modifier) {
                "left", "slight left", "sharp left" -> "Keep left$onto"
                "right", "slight right", "sharp right" -> "Keep right$onto"
                else -> "Continue$onto"
            }
            "on ramp", "off ramp" -> turnWords(modifier, "Take the ramp") + onto
            else -> {
                val turn = turnWords(modifier, "")
                if (turn.isEmpty()) "Continue straight" + (if (road.isEmpty()) "" else " on $road")
                else turn + onto
            }
        }
    }

    /** "Turn left", "Bear right"... or [fallback] when the road simply goes on. */
    private fun turnWords(modifier: String?, fallback: String): String = when (modifier) {
        "left" -> "Turn left"
        "right" -> "Turn right"
        "slight left" -> "Bear left"
        "slight right" -> "Bear right"
        "sharp left" -> "Turn sharp left"
        "sharp right" -> "Turn sharp right"
        "uturn" -> "Make a U-turn"
        else -> fallback
    }

    // ---------- Google Maps hand-off ----------

    /** Opens Google Maps straight into driving navigation (voice, live traffic). */
    fun googleMapsNavUri(latitude: Double, longitude: Double): String =
        "google.navigation:q=$latitude,$longitude&mode=d"

    /** Same destination as a web link, for a phone without the Google Maps app. */
    fun googleMapsWebUrl(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=driving"
}
