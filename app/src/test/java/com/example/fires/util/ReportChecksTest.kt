package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification
import com.example.fires.util.ReportChecks.NearbyReport
import com.example.fires.util.ReportChecks.PastReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportChecksTest {

    private val now = 1_700_000_000_000L
    private fun minutesAgo(minutes: Long) = now - minutes * 60_000L

    // The new report is at (lat, lon). 0.001 degrees of latitude is about 111 m,
    // 0.0015 is about 167 m, so they fall inside and outside the 150 m radius.
    private val lat = 14.5995
    private val lon = 120.9842

    private fun nearby(
        id: String = "a",
        northDegrees: Double = 0.0,
        status: IncidentStatus = IncidentStatus.REPORTED,
        ageMinutes: Long? = 5,
        duplicateOf: String? = null
    ) = NearbyReport(
        id = id,
        latitude = lat + northDegrees,
        longitude = lon,
        status = status,
        duplicateOf = duplicateOf,
        submittedAtMillis = ageMinutes?.let { minutesAgo(it) }
    )

    private fun duplicateOf(vararg candidates: NearbyReport) =
        ReportChecks.findDuplicate(lat, lon, candidates.toList(), now)

    // ---------- Distance ----------

    @Test fun distance_samePoint_isZero() =
        assertEquals(0.0, ReportChecks.distanceMeters(lat, lon, lat, lon), 0.001)

    @Test fun distance_oneThousandthDegreeOfLatitude_isAbout111Meters() =
        assertEquals(111.2, ReportChecks.distanceMeters(lat, lon, lat + 0.001, lon), 0.5)

    @Test fun distance_isTheSameBothWays() {
        val there = ReportChecks.distanceMeters(lat, lon, lat + 0.003, lon + 0.002)
        val back = ReportChecks.distanceMeters(lat + 0.003, lon + 0.002, lat, lon)
        assertEquals(there, back, 0.001)
    }

    // ---------- Duplicate ----------

    @Test fun noCandidates_isNotDuplicate() = assertNull(duplicateOf())

    @Test fun sameSpot_isDuplicate() = assertEquals("a", duplicateOf(nearby("a")))

    @Test fun insideRadius_isDuplicate() =
        assertEquals("a", duplicateOf(nearby("a", northDegrees = 0.001)))

    @Test fun outsideRadius_isNotDuplicate() =
        assertNull(duplicateOf(nearby("a", northDegrees = 0.0015)))

    @Test fun resolvedAndDismissed_areIgnored() {
        assertNull(duplicateOf(nearby(status = IncidentStatus.RESOLVED)))
        assertNull(duplicateOf(nearby(status = IncidentStatus.DISMISSED)))
    }

    @Test fun everyActiveStatus_counts() {
        listOf(
            IncidentStatus.REPORTED, IncidentStatus.VERIFIED,
            IncidentStatus.DISPATCHED, IncidentStatus.ON_SCENE
        ).forEach { status ->
            assertEquals("a", duplicateOf(nearby("a", status = status)))
        }
    }

    @Test fun olderThanTheWindow_isIgnored() =
        assertNull(duplicateOf(nearby(ageMinutes = ReportChecks.DUPLICATE_WINDOW_MINUTES + 1)))

    @Test fun insideTheWindow_counts() =
        assertEquals("a", duplicateOf(nearby("a", ageMinutes = ReportChecks.DUPLICATE_WINDOW_MINUTES - 1)))

    @Test fun noServerTimeYet_countsAsJustSent() =
        assertEquals("a", duplicateOf(nearby("a", ageMinutes = null)))

    @Test fun severalMatches_nearestWins() {
        val far = nearby("far", northDegrees = 0.0012)
        val near = nearby("near", northDegrees = 0.0003)
        assertEquals("near", duplicateOf(far, near))
        assertEquals("near", duplicateOf(near, far))
    }

    @Test fun nearestIsItselfADuplicate_groupsUnderItsPrimary() =
        assertEquals("primary", duplicateOf(nearby("second", duplicateOf = "primary")))

    @Test fun blankDuplicateOf_isTreatedAsNone() =
        assertEquals("a", duplicateOf(nearby("a", duplicateOf = "")))

    @Test fun ignoredCandidates_doNotHideARealMatch() {
        val resolvedRightHere = nearby("old", status = IncidentStatus.RESOLVED)
        val activeNearby = nearby("live", northDegrees = 0.001)
        assertEquals("live", duplicateOf(resolvedRightHere, activeNearby))
    }

    // ---------- Suspicious ----------

    private fun past(ageMinutes: Long?, verification: Verification = Verification.PENDING) =
        PastReport(ageMinutes?.let { minutesAgo(it) }, verification)

    private fun suspicious(past: List<PastReport>) = ReportChecks.isSuspicious(past, now)

    @Test fun firstReport_isNotSuspicious() = assertFalse(suspicious(emptyList()))

    @Test fun belowTheLimit_isNotSuspicious() {
        // One fewer earlier report than needed to reach the limit (the new report counts too).
        val earlier = ReportChecks.SUSPICIOUS_REPORT_COUNT - 2
        assertFalse(suspicious(List(earlier) { past(10) }))
    }

    @Test fun reachingTheLimitInTheWindow_isSuspicious() {
        val earlier = ReportChecks.SUSPICIOUS_REPORT_COUNT - 1
        assertTrue(suspicious(List(earlier) { past(10) }))
    }

    @Test fun reportsOlderThanTheWindow_doNotCount() {
        val old = ReportChecks.SUSPICIOUS_WINDOW_MINUTES + 1
        assertFalse(suspicious(List(10) { past(old) }))
    }

    @Test fun noServerTimeYet_countsAsJustSent() {
        val earlier = ReportChecks.SUSPICIOUS_REPORT_COUNT - 1
        assertTrue(suspicious(List(earlier) { past(null) }))
    }

    @Test fun enoughFalseReports_isSuspicious_evenIfOld() {
        val longAgo = 60L * 24 * 90 // about three months
        val falseOnes = List(ReportChecks.SUSPICIOUS_FALSE_REPORTS) { past(longAgo, Verification.FALSE) }
        assertTrue(suspicious(falseOnes))
    }

    @Test fun fewerFalseReports_isNotSuspicious() {
        val longAgo = 60L * 24 * 90
        val falseOnes = List(ReportChecks.SUSPICIOUS_FALSE_REPORTS - 1) { past(longAgo, Verification.FALSE) }
        assertFalse(suspicious(falseOnes))
    }

    @Test fun verifiedPendingAndDuplicateReports_doNotCountAsFalse() {
        val longAgo = 60L * 24 * 90
        val others = listOf(Verification.VERIFIED, Verification.PENDING, Verification.DUPLICATE)
            .flatMap { v -> List(5) { past(longAgo, v) } }
        assertFalse(suspicious(others))
    }
}
