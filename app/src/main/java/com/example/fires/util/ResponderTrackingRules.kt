package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.ResponderLocation

/**
 * What the citizen's Status tracker says about the responder (H4). Pure Kotlin, no Android
 * classes, so it runs as a plain JVM test.
 *
 * Time is compared with the citizen's phone clock, like every other "x min ago" in the app, so a
 * phone with a badly wrong clock can show the wrong age.
 */
object ResponderTrackingRules {

    /** A location older than this is shown as "last updated", not as a live ETA. */
    const val STALE_AFTER_MS = 60_000L

    const val WAITING_TEXT = "Waiting for the responder's location…"
    const val ARRIVED_TEXT = "Arrived. Responders are at the scene."

    enum class Kind { WAITING, LIVE, STALE, ARRIVED }

    /** The banner on the status screen: its wording, and what kind it is (the screen picks the colour). */
    data class Banner(val kind: Kind, val text: String)

    /** When this location was written. A write still waiting for the server has no time yet: it is brand new. */
    fun updatedAtMillis(location: ResponderLocation, nowMillis: Long): Long =
        location.updatedAt?.toDate()?.time ?: nowMillis

    fun isFresh(location: ResponderLocation, nowMillis: Long): Boolean =
        nowMillis - updatedAtMillis(location, nowMillis) < STALE_AFTER_MS

    /**
     * @return null when there is nothing to say (not dispatched yet, or closed).
     *  - ON_SCENE: "Arrived" (the responders' location documents are gone by then)
     *  - DISPATCHED with nobody sharing yet: "Waiting for the responder's location"
     *  - DISPATCHED, someone updated within the last minute: "Responder is on the way, arriving in about 6 min"
     *  - DISPATCHED, everyone older than a minute: "Location last updated 2 min ago", never a stale ETA
     */
    fun banner(status: IncidentStatus, responders: List<ResponderLocation>, nowMillis: Long): Banner? {
        if (status == IncidentStatus.ON_SCENE) return Banner(Kind.ARRIVED, ARRIVED_TEXT)
        if (status != IncidentStatus.DISPATCHED) return null
        if (responders.isEmpty()) return Banner(Kind.WAITING, WAITING_TEXT)

        val fresh = responders.filter { isFresh(it, nowMillis) }
        if (fresh.isEmpty()) {
            val newest = responders.maxOf { updatedAtMillis(it, nowMillis) }
            return Banner(Kind.STALE, "Location last updated " + timeAgoLabel(newest, nowMillis))
        }
        return Banner(Kind.LIVE, liveText(fresh))
    }

    /** One responder as the citizen's map and legend show them (H5a). */
    data class MarkerLabel(
        val responderId: String,
        /**
         * 0, 1, 2... in a fixed order (sorted by responder id), so a responder keeps the same
         * number and colour while updates come in, whatever order Firestore sends them in.
         */
        val position: Int,
        /**
         * "Responder" when only one is sharing, otherwise "Responder 1", "Responder 2"...
         * A stale one adds how old its location is: "Responder 2 (last seen 2 min ago)".
         */
        val text: String,
        val fresh: Boolean
    )

    fun markerLabels(responders: List<ResponderLocation>, nowMillis: Long): List<MarkerLabel> {
        val ordered = responders.sortedBy { it.id }
        return ordered.mapIndexed { index, responder ->
            val name = if (ordered.size == 1) "Responder" else "Responder ${index + 1}"
            val fresh = isFresh(responder, nowMillis)
            val text = if (fresh) {
                name
            } else {
                "$name (last seen ${timeAgoLabel(updatedAtMillis(responder, nowMillis), nowMillis)})"
            }
            MarkerLabel(responder.id, index, text, fresh)
        }
    }

    /** One responder: "...arriving in about 6 min". Several: the one who is nearest in time sets the ETA. */
    private fun liveText(fresh: List<ResponderLocation>): String {
        val nearest = fresh.filter { it.etaSeconds != null }.minByOrNull { it.etaSeconds!! }
        val who = if (fresh.size == 1) "Responder is" else "${fresh.size} responders are"
        val nearestWord = if (fresh.size == 1) "" else "the nearest "

        val eta = nearest?.etaSeconds
            ?: return "$who on the way" // sharing, but no route yet
        val distance = nearest.distanceMeters
        if (distance != null && distance <= RouteRules.ARRIVING_METERS) {
            return if (fresh.size == 1) "Responder is arriving now" else "$who on the way, ${nearestWord}arriving now"
        }
        return "$who on the way, ${nearestWord}arriving in ${RouteRules.etaLabel(eta)}"
    }
}
