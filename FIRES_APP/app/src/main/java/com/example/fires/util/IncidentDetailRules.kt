package com.example.fires.util

import com.example.fires.data.model.HazardType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.LocationSource
import com.example.fires.data.model.SeveritySource
import com.example.fires.data.model.VulnerableGroup
import java.util.Locale

/**
 * Wording and small decisions for the responder's Incident detail screen (E2). Pure Kotlin, so it
 * runs as a plain JVM test. The screen only draws what comes out of here.
 */
object IncidentDetailRules {

    const val NOT_PROVIDED = "Not provided"
    const val NONE_REPORTED = "None reported"

    /** Why a report is highlighted. These are the checks in ReportChecks (D5). */
    const val FLAGGED_NOTE =
        "Flagged for review: this account sent several reports in a short time, or earlier " +
            "reports from it were marked false. Check it before you send anyone."

    /** The reporter's number as typed, or a plain note when the profile has none. */
    fun contactLabel(contact: String): String = contact.trim().ifBlank { NOT_PROVIDED }

    /**
     * A "tel:" address for the phone's dialer, or null when there is no usable number. Spaces and
     * dashes are dropped, and a leading + is kept so "+63 917 123 4567" still dials.
     */
    fun dialUri(contact: String): String? {
        val trimmed = contact.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 3) return null
        return "tel:" + (if (trimmed.startsWith("+")) "+" else "") + digits
    }

    /** "1 person", "4 people", or a note when the reporter did not say. */
    fun peopleAtRiskLabel(count: Int): String = when {
        count <= 0 -> NONE_REPORTED
        count == 1 -> "1 person"
        else -> "$count people"
    }

    fun vulnerableLabel(values: List<String>): String =
        values.map { VulnerableGroup.fromValue(it)?.label ?: it }
            .joinToString(", ").ifBlank { NONE_REPORTED }

    fun hazardsLabel(values: List<String>): String =
        values.map { HazardType.fromValue(it)?.label ?: it }
            .joinToString(", ").ifBlank { NONE_REPORTED }

    /** Says who decided the severity, so a responder knows whether a person has already looked at it. */
    fun severitySourceLabel(source: String): String = when (SeveritySource.fromValue(source)) {
        SeveritySource.AUTO -> "Calculated automatically"
        SeveritySource.RESPONDER -> "Set by a responder"
    }

    fun locationSourceLabel(source: String): String = when (LocationSource.fromValue(source)) {
        LocationSource.GPS -> "Phone GPS"
        LocationSource.PIN -> "Pin placed by the reporter"
    }

    fun coordinatesLabel(latitude: Double, longitude: Double): String =
        "%.5f, %.5f".format(Locale.US, latitude, longitude)

    /**
     * The duplicates to list under a primary report, oldest first. The report itself is never in
     * its own list. A report that has no server time yet was just sent, so it goes last.
     */
    fun sortDuplicates(duplicates: List<Incident>, selfId: String): List<Incident> =
        duplicates
            .filter { it.id != selfId }
            .sortedWith(compareBy<Incident>({ it.submittedAt?.seconds ?: Long.MAX_VALUE }, { it.id }))
}
