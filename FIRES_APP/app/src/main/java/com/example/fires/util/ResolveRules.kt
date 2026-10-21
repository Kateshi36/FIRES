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

    const val RESOLVE_WARNING =
        "Resolving closes the report, ends location sharing, and saves this record to the history. " +
            "It cannot be undone."

    /** "What happened?" chips. Tapping one adds it as a line to the remarks. */
    val QUICK_NOTES = listOf(
        "Fire put out", "Handed over to BFP", "Nobody hurt", "Injuries reported",
        "Cause: electrical", "Cause: cooking"
    )

    /**
     * Adds "[note]." as a new line at the end of [remarks]. A note that is already there, or one that
     * would push the text over [MAX_REMARKS], leaves the text as it was.
     */
    fun addNote(remarks: String, note: String): String {
        val line = "$note."
        val current = remarks.trimEnd()
        if (current.lines().any { it.trim() == line }) return remarks
        val next = if (current.isEmpty()) line else current + "\n" + line
        return if (next.trim().length > MAX_REMARKS) remarks else next
    }

    /** True when [note] is already one of the lines (the chip shows as chosen). */
    fun hasNote(remarks: String, note: String): Boolean = remarks.lines().any { it.trim() == "$note." }

    /** "45 min", "1 h 12 min", "2 h". Never negative: a phone clock slightly behind counts as 0. */
    fun durationLabel(fromMillis: Long, toMillis: Long): String {
        val minutes = ((toMillis - fromMillis) / 60_000).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

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
