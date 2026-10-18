package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum

/**
 * Status filter chips on the responder dashboard (E1). The dashboard only shows ACTIVE incidents
 * (reported, verified, dispatched, on scene). Resolved and dismissed ones live in Incident history.
 */
enum class StatusFilter(val label: String, val status: IncidentStatus?) {
    ACTIVE("Active", null),
    REPORTED("Reported", IncidentStatus.REPORTED),
    VERIFIED("Verified", IncidentStatus.VERIFIED),
    DISPATCHED("Dispatched", IncidentStatus.DISPATCHED),
    ON_SCENE("On scene", IncidentStatus.ON_SCENE);

    fun matches(incidentStatus: IncidentStatus): Boolean =
        incidentStatus.isActive && (status == null || incidentStatus == status)
}

/** "Near me" options. The distance is measured from the responder's phone. */
enum class DistanceFilter(val label: String, val meters: Double?) {
    ANY("Anywhere", null),
    M500("Within 500 m", 500.0),
    KM1("Within 1 km", 1_000.0),
    KM2("Within 2 km", 2_000.0)
}

/**
 * One primary report with the duplicates that were grouped under it. A report with no duplicates is
 * a group of one. The list and the map both show one entry per group.
 */
data class IncidentGroup(val primary: Incident, val duplicates: List<Incident>) {
    val id: String get() = primary.id

    /** Highlighted on the dashboard. Only the primary decides this. */
    val isFlagged: Boolean get() = primary.isSuspicious

    /** How many of the grouped duplicates are flagged, shown as a note on the card. */
    val flaggedDuplicates: Int get() = duplicates.count { it.isSuspicious }
}

/** What the dashboard shows after grouping and filtering. */
data class DashboardView(
    /** Groups that pass both filters, most urgent first. */
    val groups: List<IncidentGroup>,
    /** How many groups each status chip would show, with the location filter applied. */
    val counts: Map<StatusFilter, Int>,
    /** Every active group before any filter, to tell "nothing reported" from "nothing matches". */
    val total: Int
)

object DashboardRules {

    /**
     * Groups the ACTIVE incidents. A duplicate is nested under its primary only when that primary is
     * itself active and not a duplicate. In every other case (the primary was resolved, is missing,
     * or the data points in a circle) the duplicate stays in the list as its own entry, so an active
     * report can never be hidden by grouping.
     */
    fun group(incidents: List<Incident>): List<IncidentGroup> {
        val active = incidents.filter { it.statusEnum().isActive }
        val activeIds = active.map { it.id }.toSet()

        fun parentOf(i: Incident): String? =
            i.duplicateOf?.takeIf { it.isNotBlank() && it != i.id && it in activeIds }

        val heads = active.filter { parentOf(it) == null }
        val headIds = heads.map { it.id }.toSet()

        val nested = active
            .filter { parentOf(it) in headIds }
            .groupBy { parentOf(it)!! }
        val nestedIds = nested.values.flatten().map { it.id }.toSet()

        // Anything not nested under a head is a head itself (covers chains and circles).
        return active.filter { it.id !in nestedIds }.map { head ->
            IncidentGroup(
                primary = head,
                duplicates = nested[head.id].orEmpty().sortedBy { sentAt(it) }
            )
        }
    }

    fun view(
        incidents: List<Incident>,
        status: StatusFilter,
        distance: DistanceFilter,
        originLatitude: Double?,
        originLongitude: Double?
    ): DashboardView {
        val all = group(incidents)

        val limit = distance.meters
        val nearby = if (limit != null && originLatitude != null && originLongitude != null) {
            all.filter { metersFrom(originLatitude, originLongitude, it) <= limit }
        } else {
            all // no limit chosen, or no position yet: do not hide anything
        }

        val shown = nearby
            .filter { status.matches(it.primary.statusEnum()) }
            .sortedWith(
                compareByDescending<IncidentGroup> { it.primary.severityEnum().rank }
                    .thenByDescending { sentAt(it.primary) }
                    .thenBy { it.id }
            )

        return DashboardView(
            groups = shown,
            counts = StatusFilter.entries.associateWith { f ->
                nearby.count { f.matches(it.primary.statusEnum()) }
            },
            total = all.size
        )
    }

    fun metersFrom(originLatitude: Double, originLongitude: Double, group: IncidentGroup): Double =
        ReportChecks.distanceMeters(
            originLatitude, originLongitude, group.primary.latitude, group.primary.longitude
        )

    /** "350 m" under a kilometre, "1.2 km" above. */
    fun distanceLabel(meters: Double): String =
        if (meters < 1_000) "${(Math.round(meters / 10.0) * 10).coerceAtLeast(10)} m"
        else "%.1f km".format(java.util.Locale.US, meters / 1_000)

    // A report that has no server time yet was just sent from this phone, so it counts as newest.
    private fun sentAt(i: Incident): Long = i.submittedAt?.seconds ?: Long.MAX_VALUE
}
