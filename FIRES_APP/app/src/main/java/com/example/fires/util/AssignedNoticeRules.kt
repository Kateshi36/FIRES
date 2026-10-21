package com.example.fires.util

/**
 * The "you were assigned" notice the administrator's website writes to users/{uid}/notifications.
 * Pure Kotlin so the freshness rule runs as a JVM test.
 */
object AssignedNoticeRules {

    /** An older notice is history (for example from before the phone was switched on), not news. */
    const val MAX_AGE_MS = 10 * 60 * 1000L

    const val TITLE = "You've been assigned"
    const val REMOVED_TITLE = "Assignment removed"

    fun title(removed: Boolean): String = if (removed) REMOVED_TITLE else TITLE

    /** createdAtMs is null while the server has not stamped the document yet: not alertable. */
    fun shouldAlert(createdAtMs: Long?, nowMs: Long, alreadyShown: Boolean): Boolean =
        !alreadyShown && createdAtMs != null && nowMs - createdAtMs in -MAX_AGE_MS..MAX_AGE_MS

    fun text(fromServer: String?, removed: Boolean = false): String =
        fromServer?.trim().takeUnless { it.isNullOrEmpty() }
            ?: if (removed) "An administrator took you off a fire report."
            else "An administrator assigned you to a fire report. Open it to respond."
}
