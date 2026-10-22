package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.statusEnum

/** One line in the citizen's Notifications list: what happened to one of my reports. */
data class CitizenUpdate(
    val incidentId: String,
    val code: String,
    val headline: String,
    val detail: String,
    /** When the report last changed (or was sent), in milliseconds. 0 when the server has not stamped it yet. */
    val whenMillis: Long
)

/**
 * The Notifications list (the bell on Home): the latest known state of each of MY reports, newest
 * change first, worded exactly like the Report status screen and the phone notifications. Pure
 * Kotlin, so it is tested on the JVM (CitizenUpdatesTest).
 */
object CitizenUpdates {

    const val MAX_ITEMS = 30
    const val EMPTY = "No updates yet. When a responder acts on one of your reports, it shows here."

    fun from(reports: List<Incident>): List<CitizenUpdate> =
        reports
            .filter { it.id.isNotBlank() }
            .map { incident ->
                val status = incident.statusEnum()
                CitizenUpdate(
                    incidentId = incident.id,
                    code = incidentCode(incident.id),
                    headline = statusHeadline(status),
                    detail = statusDetail(status),
                    whenMillis = (incident.updatedAt ?: incident.submittedAt)?.seconds?.times(1000) ?: 0L
                )
            }
            .sortedByDescending { it.whenMillis }
            .take(MAX_ITEMS)
}
