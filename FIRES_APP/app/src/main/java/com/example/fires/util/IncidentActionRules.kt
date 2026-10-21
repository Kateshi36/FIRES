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
 *   REPORTED --(administrator verifies, on the website)--> VERIFIED --> DISPATCHED --> ON_SCENE --> resolve
 * A responder never verifies, marks false or marks duplicate: only an administrator does, and the
 * Firestore rules refuse it too. A report must be verified before it can be dispatched. Resolving needs remarks, so it is not a
 * plain status button here.
 */
object IncidentActionRules {

    const val ACTION_ERROR = "We couldn't save that change. Check your connection and try again."
    const val CLOSED_NOTE = "This report is closed, so it has no more actions."

    /** Shown instead of the review buttons: only an administrator (on the website) verifies a report. */
    const val ADMIN_VERIFIES_NOTE =
        "Only an administrator can verify a report. You can respond once it is verified."

    const val WAITING_TITLE = "Waiting for an administrator"
    const val LOCKED_HINT = "Unlocks after verification. Severity can be changed then, too."
    const val DUPLICATE_NOTE = "Other reports look like the same fire. The administrator decides whether to merge them."

    /** True while the report still waits for an administrator. A responder has no review action. */
    fun awaitsAdminVerification(status: IncidentStatus): Boolean = status == IncidentStatus.REPORTED

    /** Severity is changed only on a verified, still-open report (the rules refuse it before). */
    fun canOverrideSeverity(status: IncidentStatus): Boolean =
        status.isActive && status != IncidentStatus.REPORTED

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
}
