package com.example.fires.util

import com.example.fires.data.model.IncidentStatus

/**
 * Rules and wording for resolving a report with remarks (E5). Pure Kotlin, so it runs as a plain
 * JVM test. A report can be resolved only once responders are on scene, and the remarks are
 * required: they become the text of the historical record (records/{incidentId}).
 */
object ResolveRules {

    const val MIN_REMARKS = 10
    const val MAX_REMARKS = 500

    const val MSG_REMARKS_BLANK = "Write what happened and how the fire was handled."
    const val MSG_REMARKS_SHORT = "Add a little more detail (at least $MIN_REMARKS characters)."
    const val MSG_REMARKS_LONG = "Keep the remarks under $MAX_REMARKS characters."
    const val MSG_NOT_ON_SCENE = "Only a report that is on scene can be resolved."
    const val MSG_SIGNED_OUT = "You were signed out. Log in again to resolve this report."
    const val MSG_NOT_CONFIRMED =
        "We couldn't confirm that the report was resolved. Check your connection and tap " +
            "Resolve again. Tapping again will not create a second record."

    /** Resolving is offered only while the report is on scene (the last step before resolved). */
    fun canResolve(status: IncidentStatus): Boolean = status == IncidentStatus.ON_SCENE

    fun validateRemarks(value: String): String? {
        val text = value.trim()
        return when {
            text.isEmpty() -> MSG_REMARKS_BLANK
            text.length < MIN_REMARKS -> MSG_REMARKS_SHORT
            text.length > MAX_REMARKS -> MSG_REMARKS_LONG
            else -> null
        }
    }

    /** Saved text: surrounding spaces and blank lines at the ends are not kept. */
    fun cleanRemarks(value: String): String = value.trim()
}
