package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification

/**
 * Which actions a responder may take on a report, and the wording around them (E3, E4). Pure
 * Kotlin, so it runs as a plain JVM test. The screen shows only the actions that pass here, and the
 * ViewModel checks again against the live report, so a stale tap (another responder already acted)
 * does nothing.
 *
 * The flow it enforces:
 *   REPORTED --verify--> VERIFIED --> DISPATCHED --> ON_SCENE --> (resolve, added in E5)
 *   REPORTED --false or duplicate--> DISMISSED
 * A report must be verified before it can be dispatched. Resolving needs remarks, so it is not a
 * plain status button here.
 */
object IncidentActionRules {

    const val ACTION_ERROR = "We couldn't save that change. Check your connection and try again."
    const val CLOSED_NOTE = "This report is closed, so it has no more actions."

    /** Verify, false and duplicate are only offered on a report nobody has reviewed yet. */
    fun canReview(status: IncidentStatus): Boolean = status == IncidentStatus.REPORTED

    fun canOverrideSeverity(status: IncidentStatus): Boolean = status.isActive

    fun canAssign(status: IncidentStatus): Boolean = status.isActive

    /** The one step forward, or null when there is none (not verified yet, or finished). */
    fun nextStatus(status: IncidentStatus): IncidentStatus? = when (status) {
        IncidentStatus.VERIFIED -> IncidentStatus.DISPATCHED
        IncidentStatus.DISPATCHED -> IncidentStatus.ON_SCENE
        else -> null
    }

    /** The one step back, to fix a wrong tap. It never goes back to REPORTED: verification stays. */
    fun previousStatus(status: IncidentStatus): IncidentStatus? = when (status) {
        IncidentStatus.DISPATCHED -> IncidentStatus.VERIFIED
        IncidentStatus.ON_SCENE -> IncidentStatus.DISPATCHED
        else -> null
    }

    /** Closing a report as false or duplicate takes it off the dashboard, so it asks first. */
    fun needsConfirmation(verification: Verification): Boolean =
        verification == Verification.FALSE || verification == Verification.DUPLICATE

    fun confirmTitle(verification: Verification): String = when (verification) {
        Verification.FALSE -> "Mark as false report?"
        Verification.DUPLICATE -> "Mark as duplicate?"
        else -> "Confirm"
    }

    fun confirmBody(verification: Verification): String = when (verification) {
        Verification.FALSE ->
            "The report will be closed and leave the dashboard. It also counts against this " +
                "reporter's account when later reports are checked."
        Verification.DUPLICATE ->
            "The report will be closed and leave the dashboard. Use this only when another " +
                "report already covers the same fire."
        else -> "Continue?"
    }

    fun confirmButton(verification: Verification): String = when (verification) {
        Verification.FALSE -> "Mark as false"
        Verification.DUPLICATE -> "Mark as duplicate"
        else -> "Confirm"
    }
}
