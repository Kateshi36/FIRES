package com.example.fires.util

import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.google.firebase.Timestamp

/*
 * Responder alerts (F1.1 and F1.2). Pure Kotlin: no Android classes, so every rule is tested on
 * the JVM (AlertRulesTest). The alert service (later steps) only feeds snapshots in and turns the
 * returned events into notifications.
 */

enum class AlertKind { NEW_INCIDENT, STATUS_CHANGED }

/** LOUD = sound and heads-up. QUIET = shows up without interrupting. Picks the channel (F1.4). */
enum class AlertLevel { LOUD, QUIET }

/**
 * One notification to show.
 * [incidentId] is both the notification id (so a status change replaces the earlier notification
 * for the same incident, F1.7) and the target of the tap (F1.8).
 */
data class AlertEvent(
    val incidentId: String,
    val kind: AlertKind,
    val level: AlertLevel,
    val title: String,
    val text: String
)

object AlertRules {

    /**
     * An event only alerts while it is this fresh. Guards against a flood of old alerts when a
     * snapshot is built from stale data on the phone (first start, or the service restarting
     * after Android killed it) and the server then fills in what was missing.
     * Uses the server's own timestamps, so a report that was queued offline and synced late still
     * counts as fresh (its time is the moment the server received it).
     */
    const val MAX_AGE_MS = 30 * 60 * 1000L

    private const val SEP = " \u00B7 " // " · "

    /**
     * Compares the previous active-incident list with the new one.
     *
     * @param previous the last list seen, or null when this is the FIRST snapshot. The first
     *   snapshot only sets the baseline, so opening the app does not alert for every existing fire.
     * @param current the list now.
     * @param nowMillis the current time, passed in so tests control it.
     *
     * Alerts for:
     *  - an id that was not in [previous] (a new report)
     *  - an id in both lists whose status changed
     *
     * Does NOT alert for: the first snapshot, an incident that left the list (resolved or
     * dismissed), a change to anything other than the status (severity override, edits), or
     * anything older than [MAX_AGE_MS].
     *
     * Order: new reports first (most severe first), then status changes.
     */
    fun detect(previous: List<Incident>?, current: List<Incident>, nowMillis: Long): List<AlertEvent> {
        if (previous == null) return emptyList()

        val oldStatusById = previous.associate { it.id to it.status }

        val newReports = current
            .filter { it.id !in oldStatusById && isFresh(it.submittedAt, nowMillis) }
            .sortedByDescending { it.severityEnum().rank } // stable: ties keep their order
            .map { newIncidentAlert(it) }

        val statusChanges = current
            .filter { incident ->
                val old = oldStatusById[incident.id]
                old != null && old != incident.status && isFresh(incident.updatedAt, nowMillis)
            }
            .map { statusChangeAlert(it) }

        return newReports + statusChanges
    }

    /** No timestamp yet (a write still waiting for the server) counts as fresh. */
    private fun isFresh(time: Timestamp?, nowMillis: Long): Boolean =
        time == null || nowMillis - time.seconds * 1000 <= MAX_AGE_MS

    // ---------- Wording (F1.2) ----------

    /**
     * "New fire report · Critical", then "Electrical fire · Purok 3, Bagumbayan" (plus
     * "People trapped" when the citizen said so). Always LOUD: a possible duplicate is worded
     * differently but not silenced, because the duplicate check can be wrong and a second report
     * near the first also says the fire is real.
     */
    private fun newIncidentAlert(incident: Incident): AlertEvent {
        val severity = incident.severityEnum().label
        val title = if (incident.isDuplicate) {
            "Possible duplicate report$SEP$severity"
        } else {
            "New fire report$SEP$severity"
        }
        val details = buildString {
            append(fireTypeText(incident)).append(SEP).append(addressText(incident))
            if (incident.trapped) append(SEP).append("People trapped")
        }
        return AlertEvent(incident.id, AlertKind.NEW_INCIDENT, AlertLevel.LOUD, title, details)
    }

    /** "Incident dispatched", then just the address. Always QUIET. */
    private fun statusChangeAlert(incident: Incident): AlertEvent {
        val title = when (incident.statusEnum()) {
            IncidentStatus.REPORTED -> "Incident back to reported"
            IncidentStatus.VERIFIED -> "Incident verified"
            IncidentStatus.DISPATCHED -> "Incident dispatched"
            IncidentStatus.ON_SCENE -> "Responders on scene"
            // Closed incidents leave the active list, so these are not expected here.
            IncidentStatus.RESOLVED, IncidentStatus.DISMISSED ->
                "Incident ${incident.statusEnum().label.lowercase()}"
        }
        return AlertEvent(
            incident.id, AlertKind.STATUS_CHANGED, AlertLevel.QUIET, title, addressText(incident)
        )
    }

    private fun fireTypeText(incident: Incident): String {
        val type = incident.fireTypeEnum()
        return if (type == FireType.OTHER) "Fire" else type.label
    }

    private fun addressText(incident: Incident): String =
        incident.addressText.trim().ifBlank { "Open to see the map pin" }
}
