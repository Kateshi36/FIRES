package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MyReportsRulesTest {
    private val utc = ZoneId.of("UTC")
    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
    private fun inc(id: String, status: IncidentStatus, whenMs: Long? = null) = Incident(
        id = id, status = status.value,
        submittedAt = whenMs?.let { Timestamp(it / 1000, 0) }
    )

    private val list = listOf(
        inc("a", IncidentStatus.DISPATCHED, at(2026, 10, 20)),
        inc("b", IncidentStatus.RESOLVED, at(2026, 10, 9)),
        inc("c", IncidentStatus.DISMISSED, at(2026, 9, 14)),
        inc("d", IncidentStatus.REPORTED, at(2026, 10, 1))
    )

    @Test fun the_newest_open_report_is_pinned() {
        assertEquals("a", MyReportsRules.pinned(list)?.id)
        assertNull(MyReportsRules.pinned(list.filter { it.id in setOf("b", "c") }))
    }

    @Test fun counts_split_open_and_closed() {
        val c = MyReportsRules.counts(list)
        assertEquals(4, c[ReportFilter.ALL]); assertEquals(2, c[ReportFilter.ACTIVE]); assertEquals(2, c[ReportFilter.CLOSED])
    }

    @Test fun the_list_never_repeats_the_pinned_report_and_follows_the_filter() {
        assertEquals(listOf("b", "c", "d"), MyReportsRules.listed(list, ReportFilter.ALL).map { it.id })
        assertEquals(listOf("d"), MyReportsRules.listed(list, ReportFilter.ACTIVE).map { it.id })
        assertEquals(listOf("b", "c"), MyReportsRules.listed(list, ReportFilter.CLOSED).map { it.id })
    }

    @Test fun grouping_is_by_calendar_month_and_unstamped_counts_as_new() {
        val now = at(2026, 10, 22)
        assertEquals(ReportGroup.THIS_MONTH, MyReportsRules.group(list[1], now, utc))
        assertEquals(ReportGroup.EARLIER, MyReportsRules.group(list[2], now, utc))
        assertEquals(ReportGroup.THIS_MONTH, MyReportsRules.group(inc("z", IncidentStatus.REPORTED), now, utc))
    }

    @Test fun a_closed_report_never_says_false_or_duplicate() {
        assertEquals("Closed", MyReportsRules.outcomeLabel(IncidentStatus.DISMISSED))
        assertEquals("Resolved", MyReportsRules.outcomeLabel(IncidentStatus.RESOLVED))
    }

    @Test fun progress_and_banner_follow_the_status() {
        assertEquals(3, MyReportsRules.progressSegments(IncidentStatus.DISPATCHED))
        assertEquals(1, MyReportsRules.progressSegments(IncidentStatus.REPORTED))
        assertTrue(MyReportsRules.bannerIsGood(IncidentStatus.DISPATCHED))
        assertTrue(!MyReportsRules.bannerIsGood(IncidentStatus.REPORTED))
        assertEquals("Help is on the way", MyReportsRules.bannerText(IncidentStatus.DISPATCHED))
    }

    @Test fun time_labels() {
        assertEquals("9:41 PM", MyReportsRules.timeLabel(ZonedDateTime.of(2026, 10, 22, 21, 41, 0, 0, utc).toInstant().toEpochMilli(), utc))
        assertEquals("Oct 9", MyReportsRules.dayLabel(at(2026, 10, 9), utc))
        assertEquals("INC-ABC123", incidentCode("abc123XYZ"))
    }
}
