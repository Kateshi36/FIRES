package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification
import com.example.fires.data.model.statusEnum
import com.example.fires.data.model.verificationEnum
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Status chips on the Incident history screen (E7). History lists CLOSED reports: resolved ones, and
 * the ones closed after review as a false report or a duplicate. The chip says how a report ended.
 */
enum class HistoryStatusFilter(val label: String) {
    ALL("All"),
    RESOLVED("Resolved"),
    FALSE_REPORT("False report"),
    DUPLICATE("Duplicate");

    fun matches(incident: Incident): Boolean = when (this) {
        ALL -> true
        RESOLVED -> incident.statusEnum() == IncidentStatus.RESOLVED
        FALSE_REPORT -> incident.statusEnum() == IncidentStatus.DISMISSED &&
            incident.verificationEnum() == Verification.FALSE
        DUPLICATE -> incident.statusEnum() == IncidentStatus.DISMISSED &&
            incident.verificationEnum() == Verification.DUPLICATE
    }
}

/**
 * A range of calendar days, both ends included. An open end means "no limit" on that side, and no
 * ends at all means any date. Days are in the phone's time zone.
 */
data class DateRange(val from: LocalDate? = null, val to: LocalDate? = null) {
    val isAny: Boolean get() = from == null && to == null

    fun contains(millis: Long, zone: ZoneId): Boolean {
        if (isAny) return true
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        // Tolerate the two ends being the wrong way round instead of matching nothing.
        val (lo, hi) = if (from != null && to != null && from > to) Pair(to, from) else Pair(from, to)
        return (lo == null || !day.isBefore(lo)) && (hi == null || !day.isAfter(hi))
    }

    companion object {
        val ANY = DateRange()

        /** The last [days] days, today included: lastDays(7) is today and the six days before. */
        fun lastDays(days: Int, today: LocalDate) = DateRange(today.minusDays((days - 1).toLong()), today)
    }
}

/** The quick choices in the date menu. "Choose dates" opens the calendar instead. */
enum class DatePreset(val label: String, val days: Int?) {
    ANY("Any date", null),
    LAST_7("Last 7 days", 7),
    LAST_30("Last 30 days", 30);

    fun range(today: LocalDate): DateRange = if (days == null) DateRange.ANY else DateRange.lastDays(days, today)
}

/** What the history screen shows after the date and status filters. */
data class HistoryView(
    /** Closed reports that pass both filters, newest first. */
    val incidents: List<Incident>,
    /** How many reports each status chip would show, with the date filter applied. */
    val counts: Map<HistoryStatusFilter, Int>,
    /** Every closed report before any filter, to tell "nothing closed yet" from "nothing matches". */
    val total: Int
)

object HistoryRules {

    /**
     * When the report was closed. Resolving and reviewing both set updatedAt, and nothing can change
     * a closed report afterwards, so it stays the closing time. Null means the change was just made
     * on this phone and the server has not given it a time yet.
     */
    fun closedAtMillis(incident: Incident): Long? = incident.updatedAt?.toDate()?.time

    fun view(
        incidents: List<Incident>,
        status: HistoryStatusFilter,
        range: DateRange,
        zone: ZoneId = ZoneId.systemDefault()
    ): HistoryView {
        val closed = incidents.filter { !it.statusEnum().isActive }

        // A report with no closing time yet was closed a moment ago, so a date filter never hides it.
        val inRange = closed.filter { i -> closedAtMillis(i).let { it == null || range.contains(it, zone) } }

        val shown = inRange
            .filter { status.matches(it) }
            .sortedWith(
                compareByDescending<Incident> { closedAtMillis(it) ?: Long.MAX_VALUE }.thenBy { it.id }
            )

        return HistoryView(
            incidents = shown,
            counts = HistoryStatusFilter.entries.associateWith { f -> inRange.count { f.matches(it) } },
            total = closed.size
        )
    }

    /** How the report ended, for the chip on each card. */
    fun outcomeLabel(incident: Incident): String = when (incident.statusEnum()) {
        IncidentStatus.RESOLVED -> "Resolved"
        IncidentStatus.DISMISSED -> when (incident.verificationEnum()) {
            Verification.FALSE -> "False report"
            Verification.DUPLICATE -> "Duplicate"
            else -> "Dismissed"
        }
        else -> incident.statusEnum().label
    }

    /**
     * The date picker reports each day as midnight UTC. Reading it in UTC gives back the day the
     * person tapped, whatever the phone's time zone.
     */
    fun dateFromPicker(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    private val labelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    fun rangeLabel(range: DateRange, format: (LocalDate) -> String = labelFormat::format): String {
        val from = range.from
        val to = range.to
        return when {
            from == null && to == null -> "Any date"
            from != null && to != null && from == to -> format(from)
            from != null && to != null -> "${format(from)} – ${format(to)}"
            from != null -> "From ${format(from)}"
            else -> "Until ${format(to!!)}"
        }
    }
}
