package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AssignedNotice

/** The three counts at the top of the responder dashboard. */
data class DashboardTiles(val open: Int, val awaitingAdmin: Int, val mine: Int)

/** The three segments under the counts. */
enum class DashboardTab(val label: String) { MINE("Mine"), ALL("All open"), MAP("Map") }

/**
 * Pure rules for the responder dashboard (round 2 design): which reports are "mine", the counts,
 * the "You were assigned" banner and the bell's red dot. Plain Kotlin so it runs as a JVM test.
 */
object ResponderDashboardRules {

    /** The banner only shows for a recent assignment. */
    const val BANNER_MAX_AGE_MS = 60 * 60 * 1000L

    /** A group is mine when I am assigned to its primary report or to one of its duplicates. */
    fun isMine(group: IncidentGroup, mineIds: Set<String>): Boolean =
        group.primary.id in mineIds || group.duplicates.any { it.id in mineIds }

    fun mine(groups: List<IncidentGroup>, mineIds: Set<String>): List<IncidentGroup> =
        groups.filter { isMine(it, mineIds) }

    /** A report nobody has verified yet. Only an administrator can verify it. */
    fun awaitingAdmin(group: IncidentGroup): Boolean = group.primary.statusEnum() == IncidentStatus.REPORTED

    fun tiles(groups: List<IncidentGroup>, mineIds: Set<String>) = DashboardTiles(
        open = groups.size,
        awaitingAdmin = groups.count { awaitingAdmin(it) },
        mine = mine(groups, mineIds).size
    )

    /**
     * The notice behind the black "You were assigned" banner: the newest, still-fresh "assigned"
     * notice for a report that is open on the dashboard and still mine, and not already dismissed.
     * A removal (assignment deleted) makes the report stop being mine, so its banner goes too.
     */
    fun bannerNotice(
        notices: List<AssignedNotice>,
        groups: List<IncidentGroup>,
        mineIds: Set<String>,
        nowMillis: Long,
        dismissedId: String?
    ): AssignedNotice? {
        val openIds = groups.map { it.id }.toSet()
        return notices
            .filter { !it.removed && it.id != dismissedId }
            .filter { it.incidentId in openIds && it.incidentId in mineIds }
            .filter { (it.createdAtMs ?: return@filter false).let { t -> nowMillis - t in -BANNER_MAX_AGE_MS..BANNER_MAX_AGE_MS } }
            .maxByOrNull { it.createdAtMs ?: 0L }
    }

    /** The red dot on the bell: a notice newer than the last one the responder looked at. */
    fun hasUnread(notices: List<AssignedNotice>, seenMillis: Long): Boolean =
        notices.any { (it.createdAtMs ?: 0L) > seenMillis }

    fun newestMillis(notices: List<AssignedNotice>): Long = notices.maxOfOrNull { it.createdAtMs ?: 0L } ?: 0L

    /** "Hi, Reyes": the first word of the name. */
    fun greetingName(fullName: String): String =
        fullName.trim().split(Regex("\\s+")).firstOrNull { it.isNotEmpty() } ?: "responder"

    const val EMPTY_MINE = "Nothing is assigned to you yet. Open “All open” to see every report."
}
