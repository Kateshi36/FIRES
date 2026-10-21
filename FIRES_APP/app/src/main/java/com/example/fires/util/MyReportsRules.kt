package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.statusEnum
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The three chips above the list on "My reports". */
enum class ReportFilter(val label: String) { ALL("All"), ACTIVE("Active"), CLOSED("Closed") }

/** The two headings the list is grouped under. */
enum class ReportGroup(val label: String) { THIS_MONTH("THIS MONTH"), EARLIER("EARLIER") }

/**
 * Pure rules behind the "My reports" screen (round 2 mockup): which report is pinned on top, the
 * filter counts, the grouping by month and the words on the progress card. Plain Kotlin so it runs
 * as a JVM test. Lists are newest first, as IncidentRepository.observeMine returns them.
 */
object MyReportsRules {

    fun isActive(incident: Incident): Boolean = incident.statusEnum().isActive

    /** The newest report that is still open. It gets the big card on top. */
    fun pinned(reports: List<Incident>): Incident? = reports.firstOrNull { isActive(it) }

    fun counts(reports: List<Incident>): Map<ReportFilter, Int> = mapOf(
        ReportFilter.ALL to reports.size,
        ReportFilter.ACTIVE to reports.count { isActive(it) },
        ReportFilter.CLOSED to reports.count { !isActive(it) }
    )

    /** What the list shows: the filter applied, without the pinned report (it has its own card). */
    fun listed(reports: List<Incident>, filter: ReportFilter): List<Incident> {
        val pinnedId = pinned(reports)?.id
        return reports.filter { it.id != pinnedId }.filter {
            when (filter) {
                ReportFilter.ALL -> true
                ReportFilter.ACTIVE -> isActive(it)
                ReportFilter.CLOSED -> !isActive(it)
            }
        }
    }

    /** A report with no server time yet was just sent from this phone: it counts as this month. */
    fun group(incident: Incident, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ReportGroup {
        val sent = incident.submittedAt?.toDate()?.time ?: return ReportGroup.THIS_MONTH
        val a = Instant.ofEpochMilli(sent).atZone(zone)
        val b = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return if (a.year == b.year && a.month == b.month) ReportGroup.THIS_MONTH else ReportGroup.EARLIER
    }

    /** What the status chip on a row says. */
    fun outcomeLabel(status: IncidentStatus): String = when (status) {
        IncidentStatus.RESOLVED -> "Resolved"
        // Deliberately not "false" or "duplicate": the reporter is not told which (see statusDetail).
        IncidentStatus.DISMISSED -> "Closed"
        else -> status.label
    }

    /** How many of the four progress segments are filled: Received, Verified, Dispatched, On scene. */
    fun progressSegments(status: IncidentStatus): Int = when (status) {
        IncidentStatus.REPORTED -> 1
        IncidentStatus.VERIFIED -> 2
        IncidentStatus.DISPATCHED -> 3
        IncidentStatus.ON_SCENE -> 4
        IncidentStatus.RESOLVED -> 4
        IncidentStatus.DISMISSED -> 0
    }

    val PROGRESS_LABELS = listOf("Received", "Verified", "Dispatched", "On scene")

    /** The line in the coloured box of the pinned card. */
    fun bannerText(status: IncidentStatus): String = when (status) {
        IncidentStatus.REPORTED -> "Waiting for an administrator to verify"
        IncidentStatus.VERIFIED -> "Verified. Responders are getting ready"
        IncidentStatus.DISPATCHED -> "Help is on the way"
        IncidentStatus.ON_SCENE -> "Responders are at the scene"
        IncidentStatus.RESOLVED -> "This fire has been dealt with"
        IncidentStatus.DISMISSED -> "This report was closed"
    }

    /** Green box only once help is moving; before that it is neutral. */
    fun bannerIsGood(status: IncidentStatus): Boolean =
        status == IncidentStatus.DISPATCHED || status == IncidentStatus.ON_SCENE

    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /** "9:41 PM" */
    fun timeLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "Oct 9" */
    fun dayLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
}
