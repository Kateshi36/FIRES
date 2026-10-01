package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Duplicate and suspicious report checks (D5). Pure Kotlin, so it runs as a plain JVM test.
 *
 * These checks only ever set FLAGS on a report. A flagged report is still saved and still reaches
 * the responders, who see the flags and decide. Nothing here can block or delete a report.
 *
 * The three signals named in Chapter 1 are covered:
 *   - location proximity -> duplicate: an unresolved report close by
 *   - time               -> duplicate: only recent reports count. Suspicious: reports in a short window
 *   - reporter info      -> suspicious: how many reports this account sent, and how many were false
 *
 * Every number is a named constant below, so tuning with B-FLARE / BDRRMO means changing this
 * file and nothing else (the project plan lists the radius and time window as open items).
 */
object ReportChecks {

    // ---------- Duplicate: tune with B-FLARE ----------

    /** A report this close (in metres) to an unresolved one is flagged as a duplicate. */
    const val DUPLICATE_RADIUS_METERS = 150.0

    /** Only reports sent within this many minutes are compared. */
    const val DUPLICATE_WINDOW_MINUTES = 30L
    const val DUPLICATE_WINDOW_MILLIS = DUPLICATE_WINDOW_MINUTES * 60_000L

    // ---------- Suspicious: tune with B-FLARE ----------

    /** Look at the reports this account sent within this many minutes... */
    const val SUSPICIOUS_WINDOW_MINUTES = 60L
    const val SUSPICIOUS_WINDOW_MILLIS = SUSPICIOUS_WINDOW_MINUTES * 60_000L

    /** ...and flag when the new one would be this many (counting the new one) or more. */
    const val SUSPICIOUS_REPORT_COUNT = 3

    /** Also flag when this many of the account's earlier reports were marked false. */
    const val SUSPICIOUS_FALSE_REPORTS = 2

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** What the duplicate check needs to know about someone else's report. */
    data class NearbyReport(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val status: IncidentStatus,
        /** Set when this report is itself a duplicate: the id of the report it belongs to. */
        val duplicateOf: String?,
        /** Null while the server has not stamped the time yet, which means "just now". */
        val submittedAtMillis: Long?
    )

    /** What the suspicious check needs to know about this account's earlier reports. */
    data class PastReport(
        /** Null while the server has not stamped the time yet, which means "just now". */
        val submittedAtMillis: Long?,
        val verification: Verification
    )

    /**
     * Is there an unresolved report close by that was sent recently? Returns the id of the
     * PRIMARY report to group this one under, or null when this is not a duplicate.
     *
     * With several matches the nearest one wins. If that one is itself a duplicate, this report
     * is grouped under ITS primary, so a group is always one primary with its duplicates
     * (never a chain of duplicates of duplicates).
     */
    fun findDuplicate(
        latitude: Double,
        longitude: Double,
        candidates: List<NearbyReport>,
        nowMillis: Long
    ): String? {
        val since = nowMillis - DUPLICATE_WINDOW_MILLIS
        val nearest = candidates
            .filter { it.status.isActive } // resolved and dismissed reports are finished
            .filter { it.submittedAtMillis == null || it.submittedAtMillis >= since }
            .map { it to distanceMeters(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, meters) -> meters <= DUPLICATE_RADIUS_METERS }
            .minByOrNull { (_, meters) -> meters }
            ?.first
            ?: return null

        return nearest.duplicateOf?.takeIf { it.isNotBlank() } ?: nearest.id
    }

    /**
     * Should this account's new report be flagged? Yes when it sent too many reports in a short
     * time, or when several of its earlier reports were marked false by a responder.
     *
     * [past] is this account's earlier reports, NOT including the one being sent now.
     */
    fun isSuspicious(past: List<PastReport>, nowMillis: Long): Boolean {
        val since = nowMillis - SUSPICIOUS_WINDOW_MILLIS
        val recent = past.count { it.submittedAtMillis == null || it.submittedAtMillis >= since }
        val tooMany = recent + 1 >= SUSPICIOUS_REPORT_COUNT // +1 is the report being sent now

        val falseOnes = past.count { it.verification == Verification.FALSE }
        val knownFalse = falseOnes >= SUSPICIOUS_FALSE_REPORTS

        return tooMany || knownFalse
    }

    /** Straight-line distance between two GPS points in metres (haversine formula). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
