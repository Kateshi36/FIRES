package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryRulesTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val manila: ZoneId = ZoneId.of("Asia/Manila") // UTC+8

    private fun seconds(date: String, time: String = "12:00:00", zone: ZoneId = utc): Long =
        java.time.ZonedDateTime.of(LocalDate.parse(date), java.time.LocalTime.parse(time), zone).toEpochSecond()

    private fun inc(
        id: String,
        status: IncidentStatus = IncidentStatus.RESOLVED,
        verification: Verification = Verification.VERIFIED,
        closed: Long? = seconds("2026-09-15")
    ) = Incident(
        id = id, status = status.value, verification = verification.value,
        updatedAt = closed?.let { Timestamp(it, 0) }
    )

    private fun ids(view: HistoryView) = view.incidents.map { it.id }

    private val all = HistoryStatusFilter.ALL
    private val any = DateRange.ANY

    // ---------- Which reports are history ----------

    @Test fun only_closed_reports_are_listed() {
        val list = listOf(
            inc("resolved"), inc("dismissed", IncidentStatus.DISMISSED, Verification.FALSE),
            inc("reported", IncidentStatus.REPORTED, Verification.PENDING),
            inc("on_scene", IncidentStatus.ON_SCENE), inc("dispatched", IncidentStatus.DISPATCHED)
        )
        val view = HistoryRules.view(list, all, any, utc)
        assertEquals(setOf("resolved", "dismissed"), ids(view).toSet())
        assertEquals(2, view.total)
    }

    @Test fun newest_closed_comes_first_and_ties_break_by_id() {
        val list = listOf(
            inc("old", closed = seconds("2026-09-01")),
            inc("b", closed = seconds("2026-09-20")),
            inc("a", closed = seconds("2026-09-20")),
            inc("mid", closed = seconds("2026-09-10"))
        )
        assertEquals(listOf("a", "b", "mid", "old"), ids(HistoryRules.view(list, all, any, utc)))
    }

    @Test fun a_report_with_no_closing_time_yet_is_newest_and_never_hidden_by_a_date() {
        val list = listOf(inc("pending", closed = null), inc("old", closed = seconds("2026-01-01")))
        val range = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(listOf("pending"), ids(HistoryRules.view(list, all, range, utc)))
        assertEquals(listOf("pending", "old"), ids(HistoryRules.view(list, all, any, utc)))
    }

    // ---------- Status filter ----------

    private val mixed = listOf(
        inc("r1"), inc("r2"),
        inc("f1", IncidentStatus.DISMISSED, Verification.FALSE),
        inc("d1", IncidentStatus.DISMISSED, Verification.DUPLICATE)
    )

    @Test fun each_status_chip_shows_only_its_own_outcome() {
        fun shown(f: HistoryStatusFilter) = ids(HistoryRules.view(mixed, f, any, utc)).toSet()
        assertEquals(setOf("r1", "r2", "f1", "d1"), shown(HistoryStatusFilter.ALL))
        assertEquals(setOf("r1", "r2"), shown(HistoryStatusFilter.RESOLVED))
        assertEquals(setOf("f1"), shown(HistoryStatusFilter.FALSE_REPORT))
        assertEquals(setOf("d1"), shown(HistoryStatusFilter.DUPLICATE))
    }

    @Test fun chip_counts_follow_the_date_filter_but_not_the_status_filter() {
        val list = mixed + inc("old", closed = seconds("2026-01-05"))
        val range = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        val view = HistoryRules.view(list, HistoryStatusFilter.DUPLICATE, range, utc)
        assertEquals(4, view.counts[HistoryStatusFilter.ALL])
        assertEquals(2, view.counts[HistoryStatusFilter.RESOLVED])
        assertEquals(1, view.counts[HistoryStatusFilter.FALSE_REPORT])
        assertEquals(1, view.counts[HistoryStatusFilter.DUPLICATE])
        assertEquals(5, view.total) // total ignores every filter
    }

    // ---------- Date filter ----------

    @Test fun the_range_includes_both_end_days() {
        val range = DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12))
        fun hit(day: String, time: String) = range.contains(seconds(day, time) * 1000, utc)
        assertFalse(hit("2026-09-09", "23:59:59"))
        assertTrue(hit("2026-09-10", "00:00:00"))
        assertTrue(hit("2026-09-12", "23:59:59"))
        assertFalse(hit("2026-09-13", "00:00:00"))
    }

    @Test fun days_are_counted_in_the_given_time_zone() {
        // 11 Sep 20:00 UTC is already 12 Sep 04:00 in Manila.
        val millis = seconds("2026-09-11", "20:00:00", utc) * 1000
        val onTwelfth = DateRange(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12))
        assertFalse(onTwelfth.contains(millis, utc))
        assertTrue(onTwelfth.contains(millis, manila))
    }

    @Test fun open_ended_ranges_and_swapped_ends_still_work() {
        val millis = seconds("2026-09-15") * 1000
        assertTrue(DateRange(from = LocalDate.of(2026, 9, 1)).contains(millis, utc))
        assertFalse(DateRange(from = LocalDate.of(2026, 10, 1)).contains(millis, utc))
        assertTrue(DateRange(to = LocalDate.of(2026, 9, 30)).contains(millis, utc))
        assertFalse(DateRange(to = LocalDate.of(2026, 9, 1)).contains(millis, utc))
        // From after to: treated as the same range the other way round.
        assertTrue(DateRange(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)).contains(millis, utc))
    }

    @Test fun date_and_status_filters_work_together() {
        val list = listOf(
            inc("in_resolved", closed = seconds("2026-09-15")),
            inc("in_false", IncidentStatus.DISMISSED, Verification.FALSE, seconds("2026-09-15")),
            inc("out_resolved", closed = seconds("2026-08-01"))
        )
        val range = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(listOf("in_resolved"), ids(HistoryRules.view(list, HistoryStatusFilter.RESOLVED, range, utc)))
    }

    // ---------- Presets, labels, picker ----------

    @Test fun quick_ranges_count_back_from_today_including_today() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals(DateRange(LocalDate.of(2026, 9, 25), today), DatePreset.LAST_7.range(today))
        assertEquals(DateRange(LocalDate.of(2026, 9, 2), today), DatePreset.LAST_30.range(today))
        assertTrue(DatePreset.ANY.range(today).isAny)
    }

    @Test fun picker_days_are_read_in_utc_so_the_tapped_day_is_kept() {
        // The picker gives midnight UTC of the tapped day, whatever the phone's zone.
        val midnightUtc = seconds("2026-09-12", "00:00:00", utc) * 1000
        assertEquals(LocalDate.of(2026, 9, 12), HistoryRules.dateFromPicker(midnightUtc))
    }

    @Test fun range_labels_read_naturally() {
        val f: (LocalDate) -> String = { it.toString() }
        assertEquals("Any date", HistoryRules.rangeLabel(DateRange.ANY, f))
        assertEquals("2026-09-12", HistoryRules.rangeLabel(DateRange(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12)), f))
        assertEquals("2026-09-01 – 2026-09-30", HistoryRules.rangeLabel(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), f))
        assertEquals("From 2026-09-01", HistoryRules.rangeLabel(DateRange(from = LocalDate.of(2026, 9, 1)), f))
        assertEquals("Until 2026-09-30", HistoryRules.rangeLabel(DateRange(to = LocalDate.of(2026, 9, 30)), f))
    }

    @Test fun outcome_labels_say_how_the_report_ended() {
        assertEquals("Resolved", HistoryRules.outcomeLabel(inc("a")))
        assertEquals("False report", HistoryRules.outcomeLabel(inc("b", IncidentStatus.DISMISSED, Verification.FALSE)))
        assertEquals("Duplicate", HistoryRules.outcomeLabel(inc("c", IncidentStatus.DISMISSED, Verification.DUPLICATE)))
        assertEquals("Dismissed", HistoryRules.outcomeLabel(inc("d", IncidentStatus.DISMISSED, Verification.PENDING)))
    }
}
