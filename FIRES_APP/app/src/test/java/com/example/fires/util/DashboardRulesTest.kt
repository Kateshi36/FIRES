package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRulesTest {

    private fun inc(
        id: String,
        status: IncidentStatus = IncidentStatus.REPORTED,
        severity: Severity = Severity.MEDIUM,
        seconds: Long? = 1_000,
        duplicateOf: String? = null,
        suspicious: Boolean = false,
        lat: Double = 14.6000,
        lon: Double = 120.9800
    ) = Incident(
        id = id, status = status.value, severity = severity.value,
        submittedAt = seconds?.let { Timestamp(it, 0) },
        duplicateOf = duplicateOf, isDuplicate = duplicateOf != null,
        isSuspicious = suspicious, latitude = lat, longitude = lon
    )

    private fun ids(groups: List<IncidentGroup>) = groups.map { it.id }
    private fun view(
        list: List<Incident>,
        status: StatusFilter = StatusFilter.ACTIVE,
        distance: DistanceFilter = DistanceFilter.ANY,
        origin: Pair<Double, Double>? = null
    ) = DashboardRules.view(list, status, distance, origin?.first, origin?.second)

    // ---------- Grouping ----------

    @Test fun duplicate_isNestedUnderItsPrimary_notListedOnItsOwn() {
        val groups = DashboardRules.group(listOf(inc("A"), inc("B", duplicateOf = "A"), inc("C", duplicateOf = "A")))
        assertEquals(listOf("A"), ids(groups))
        assertEquals(listOf("B", "C"), groups.single().duplicates.map { it.id })
    }

    @Test fun duplicates_areListedOldestFirst() {
        val groups = DashboardRules.group(
            listOf(inc("A"), inc("late", duplicateOf = "A", seconds = 3_000), inc("early", duplicateOf = "A", seconds = 2_000))
        )
        assertEquals(listOf("early", "late"), groups.single().duplicates.map { it.id })
    }

    @Test fun duplicateOfAnUnknownPrimary_staysVisibleOnItsOwn() {
        assertEquals(listOf("B"), ids(DashboardRules.group(listOf(inc("B", duplicateOf = "gone")))))
    }

    @Test fun duplicateOfAResolvedPrimary_staysVisibleOnItsOwn() {
        // The primary is finished and leaves the dashboard. The still-active duplicate must not vanish with it.
        val groups = DashboardRules.group(listOf(inc("A", status = IncidentStatus.RESOLVED), inc("B", duplicateOf = "A")))
        assertEquals(listOf("B"), ids(groups))
        assertTrue(groups.single().duplicates.isEmpty())
    }

    @Test fun circularOrSelfPointingData_neverHidesAReport() {
        val circle = DashboardRules.group(listOf(inc("A", duplicateOf = "B"), inc("B", duplicateOf = "A")))
        assertEquals(setOf("A", "B"), ids(circle).toSet())
        assertEquals(listOf("S"), ids(DashboardRules.group(listOf(inc("S", duplicateOf = "S")))))
    }

    @Test fun chainOfDuplicates_neverHidesAReport() {
        // C -> B -> A. B nests under A. C cannot nest under a duplicate, so it stays visible.
        val groups = DashboardRules.group(listOf(inc("A"), inc("B", duplicateOf = "A"), inc("C", duplicateOf = "B")))
        assertEquals(setOf("A", "C"), ids(groups).toSet())
    }

    @Test fun closedIncidents_areNotOnTheDashboard() {
        val groups = DashboardRules.group(
            listOf(inc("A"), inc("R", status = IncidentStatus.RESOLVED), inc("D", status = IncidentStatus.DISMISSED))
        )
        assertEquals(listOf("A"), ids(groups))
    }

    // ---------- Flags ----------

    @Test fun flaggedPrimary_isHighlighted_flaggedDuplicateOnlyCounted() {
        val g = DashboardRules.group(listOf(inc("A"), inc("B", duplicateOf = "A", suspicious = true))).single()
        assertFalse(g.isFlagged)
        assertEquals(1, g.flaggedDuplicates)
        assertTrue(DashboardRules.group(listOf(inc("X", suspicious = true))).single().isFlagged)
    }

    // ---------- Status filter and counts ----------

    @Test fun statusFilter_showsOnlyThatStatus() {
        val list = listOf(
            inc("r", IncidentStatus.REPORTED), inc("v", IncidentStatus.VERIFIED),
            inc("d", IncidentStatus.DISPATCHED), inc("o", IncidentStatus.ON_SCENE)
        )
        assertEquals(setOf("r", "v", "d", "o"), ids(view(list).groups).toSet())
        assertEquals(listOf("d"), ids(view(list, StatusFilter.DISPATCHED).groups))
    }

    @Test fun counts_ignoreTheStatusFilter_soEveryChipShowsItsOwnNumber() {
        val list = listOf(inc("r1"), inc("r2"), inc("v", IncidentStatus.VERIFIED))
        val v = view(list, StatusFilter.VERIFIED)
        assertEquals(1, v.groups.size)
        assertEquals(3, v.counts[StatusFilter.ACTIVE])
        assertEquals(2, v.counts[StatusFilter.REPORTED])
        assertEquals(1, v.counts[StatusFilter.VERIFIED])
        assertEquals(0, v.counts[StatusFilter.ON_SCENE])
    }

    @Test fun groupIsFilteredByItsPrimary_duplicatesTravelWithIt() {
        val list = listOf(inc("A", IncidentStatus.DISPATCHED), inc("B", IncidentStatus.REPORTED, duplicateOf = "A"))
        assertEquals(listOf("A"), ids(view(list, StatusFilter.DISPATCHED).groups))
        assertTrue(view(list, StatusFilter.REPORTED).groups.isEmpty())
        assertEquals(1, view(list).counts[StatusFilter.ACTIVE]) // one group, not two reports
    }

    // ---------- Location filter ----------

    // About 0.009 degrees of latitude is 1 km.
    private val origin = 14.6000 to 120.9800
    private val near = inc("near", lat = 14.6020)   // ~220 m
    private val mid = inc("mid", lat = 14.6070)     // ~780 m
    private val far = inc("far", lat = 14.6300)     // ~3.3 km

    @Test fun distanceFilter_keepsOnlyReportsInsideTheRadius() {
        val list = listOf(near, mid, far)
        assertEquals(setOf("near"), ids(view(list, distance = DistanceFilter.M500, origin = origin).groups).toSet())
        assertEquals(setOf("near", "mid"), ids(view(list, distance = DistanceFilter.KM1, origin = origin).groups).toSet())
        assertEquals(setOf("near", "mid"), ids(view(list, distance = DistanceFilter.KM2, origin = origin).groups).toSet())
        assertEquals(3, view(list, distance = DistanceFilter.ANY, origin = origin).groups.size)
    }

    @Test fun distanceFilter_withoutAPosition_hidesNothing() =
        assertEquals(3, view(listOf(near, mid, far), distance = DistanceFilter.M500, origin = null).groups.size)

    @Test fun counts_respectTheLocationFilter_andTotalDoesNot() {
        val v = view(listOf(near, mid, far), distance = DistanceFilter.M500, origin = origin)
        assertEquals(1, v.counts[StatusFilter.ACTIVE])
        assertEquals(3, v.total)
    }

    // ---------- Order ----------

    @Test fun mostSevereFirst_thenNewestFirst() {
        val list = listOf(
            inc("lowNew", severity = Severity.LOW, seconds = 5_000),
            inc("critOld", severity = Severity.CRITICAL, seconds = 1_000),
            inc("medNew", severity = Severity.MEDIUM, seconds = 4_000),
            inc("medOld", severity = Severity.MEDIUM, seconds = 2_000),
            inc("highAny", severity = Severity.HIGH, seconds = 3_000)
        )
        assertEquals(listOf("critOld", "highAny", "medNew", "medOld", "lowNew"), ids(view(list).groups))
    }

    @Test fun reportWithoutServerTime_countsAsNewest() {
        val list = listOf(inc("old", seconds = 1_000), inc("justSent", seconds = null))
        assertEquals(listOf("justSent", "old"), ids(view(list).groups))
    }

    // ---------- Distance label ----------

    @Test fun distanceLabel_metresThenKilometres() {
        assertEquals("350 m", DashboardRules.distanceLabel(347.0))
        assertEquals("10 m", DashboardRules.distanceLabel(2.0))
        assertEquals("1.2 km", DashboardRules.distanceLabel(1_234.0))
        assertEquals("12.0 km", DashboardRules.distanceLabel(12_000.0))
    }
}
