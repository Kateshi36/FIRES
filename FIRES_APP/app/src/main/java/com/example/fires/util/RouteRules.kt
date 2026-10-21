package com.example.fires.util

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
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

    // ---------- The responder's own path, shared with the citizen ----------

    /** Most numbers (latitude, longitude, latitude...) shared per route: 120 points. The rules allow 240. */
    const val MAX_SHARED_PATH_NUMBERS = 240

    /**
     * The responder's current road route as a short list of numbers for Firestore, or null when
     * there is no real route (a straight-line estimate is never shared). The route is thinned
     * with Douglas-Peucker: points that sit within a few metres of the line between their
     * neighbours are dropped, and the tolerance grows until the path fits.
     */
    fun pathToShare(route: Route?): List<Double>? {
        if (route == null || route.isEstimate || route.points.size < 2) return null
        var tolerance = 5.0
        var kept = simplify(route.points, tolerance)
        while (kept.size * 2 > MAX_SHARED_PATH_NUMBERS && tolerance < 5_000.0) {
            tolerance *= 2
            kept = simplify(route.points, tolerance)
        }
        if (kept.size * 2 > MAX_SHARED_PATH_NUMBERS) return null
        return kept.flatMap { listOf(round5(it.latitude), round5(it.longitude)) }
    }

    /** The other direction, for the citizen's map. Anything malformed gives an empty list. */
    fun pathFromShared(numbers: List<Double>?): List<LatLon> {
        if (numbers == null || numbers.size < 4 || numbers.size % 2 != 0) return emptyList()
        return numbers.chunked(2).map { LatLon(it[0], it[1]) }
    }

    // ---------- The trail the responder has actually driven ----------

    /** A new trail point only after this much movement, so standing still adds nothing. */
    const val TRAIL_MIN_GAP_METERS = 25.0

    /** Recent points kept: 60 points is 120 numbers, which is what the rules allow. */
    const val TRAIL_MAX_POINTS = 60

    /** [trail] plus [next], unless [next] is too close to the last point. Oldest points drop off. */
    fun appendToTrail(trail: List<LatLon>, next: LatLon): List<LatLon> {
        val last = trail.lastOrNull()
        if (last != null &&
            LocationShareRules.distanceMeters(last.latitude, last.longitude, next.latitude, next.longitude) < TRAIL_MIN_GAP_METERS
        ) return trail
        return (trail + next).takeLast(TRAIL_MAX_POINTS)
    }

    /** The trail as latitude, longitude, latitude... for Firestore. Null until there are two points. */
    fun trailToShare(trail: List<LatLon>): List<Double>? {
        if (trail.size < 2) return null
        return trail.takeLast(TRAIL_MAX_POINTS).flatMap { listOf(round5(it.latitude), round5(it.longitude)) }
    }

    internal fun simplify(points: List<LatLon>, toleranceMeters: Double): List<LatLon> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, points.lastIndex))
        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val first = range[0]
            val last = range[1]
            var worst = -1.0
            var index = -1
            for (i in first + 1 until last) {
                val d = distanceToSegmentMeters(points[i], points[first], points[last])
                if (d > worst) {
                    worst = d
                    index = i
                }
            }
            if (index != -1 && worst > toleranceMeters) {
                keep[index] = true
                stack.addLast(intArrayOf(first, index))
                stack.addLast(intArrayOf(index, last))
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** Distance from [p] to the segment a-b, in metres (flat approximation, fine at city scale). */
    private fun distanceToSegmentMeters(p: LatLon, a: LatLon, b: LatLon): Double {
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * metersPerDegreeLng
        val by = (b.latitude - a.latitude) * metersPerDegreeLat
        val px = (p.longitude - a.longitude) * metersPerDegreeLng
        val py = (p.latitude - a.latitude) * metersPerDegreeLat
        val lengthSquared = bx * bx + by * by
        if (lengthSquared == 0.0) return hypot(px, py)
        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }

    private fun round5(value: Double): Double = Math.round(value * 100_000.0) / 100_000.0

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
