package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.repository.AssignedNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponderDashboardRulesTest {
    private fun group(id: String, status: IncidentStatus, dups: List<String> = emptyList()) = IncidentGroup(
        Incident(id = id, status = status.value),
        dups.map { Incident(id = it, duplicateOf = id, isDuplicate = true) }
    )
    private val a = group("a", IncidentStatus.DISPATCHED)
    private val b = group("b", IncidentStatus.REPORTED, listOf("b2"))
    private val c = group("c", IncidentStatus.REPORTED)
    private val groups = listOf(a, b, c)
    private val now = 10_000_000L

    private fun notice(id: String, inc: String, at: Long?, removed: Boolean = false) =
        AssignedNotice(id, inc, null, at, removed)

    @Test fun mine_includes_duplicates_assigned_to_me() {
        assertEquals(listOf("a", "b"), ResponderDashboardRules.mine(groups, setOf("a", "b2")).map { it.id })
    }

    @Test fun tiles_count_open_awaiting_and_mine() {
        val t = ResponderDashboardRules.tiles(groups, setOf("a"))
        assertEquals(DashboardTiles(open = 3, awaitingAdmin = 2, mine = 1), t)
    }

    @Test fun banner_is_the_newest_fresh_assignment_that_is_still_mine() {
        val notices = listOf(
            notice("n1", "a", now - 5_000),
            notice("n2", "c", now - 1_000),            // not mine
            notice("n3", "b", now - 2_000, removed = true)
        )
        assertEquals("n1", ResponderDashboardRules.bannerNotice(notices, groups, setOf("a"), now, null)?.id)
    }

    @Test fun banner_hides_when_old_dismissed_closed_or_unstamped() {
        val mine = setOf("a")
        val old = notice("n1", "a", now - ResponderDashboardRules.BANNER_MAX_AGE_MS - 1)
        assertNull(ResponderDashboardRules.bannerNotice(listOf(old), groups, mine, now, null))
        assertNull(ResponderDashboardRules.bannerNotice(listOf(notice("n1", "a", now)), groups, mine, now, "n1"))
        assertNull(ResponderDashboardRules.bannerNotice(listOf(notice("n1", "a", null)), groups, mine, now, null))
        assertNull(ResponderDashboardRules.bannerNotice(listOf(notice("n1", "zzz", now)), groups, setOf("zzz"), now, null))
    }

    @Test fun unread_dot_follows_the_last_seen_time() {
        val notices = listOf(notice("n1", "a", 500), notice("n2", "a", 900))
        assertTrue(ResponderDashboardRules.hasUnread(notices, 899))
        assertFalse(ResponderDashboardRules.hasUnread(notices, 900))
        assertEquals(900L, ResponderDashboardRules.newestMillis(notices))
        assertEquals(0L, ResponderDashboardRules.newestMillis(emptyList()))
    }

    @Test fun greeting_uses_the_first_word() {
        assertEquals("Juan", ResponderDashboardRules.greetingName("  Juan  Dela Cruz"))
        assertEquals("responder", ResponderDashboardRules.greetingName(" "))
    }
}
