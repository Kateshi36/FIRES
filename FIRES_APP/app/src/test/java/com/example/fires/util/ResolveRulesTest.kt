package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveRulesTest {

    @Test fun only_an_on_scene_report_can_be_resolved() {
        assertTrue(ResolveRules.canResolve(IncidentStatus.ON_SCENE))
        IncidentStatus.entries.filter { it != IncidentStatus.ON_SCENE }.forEach {
            assertFalse("$it", ResolveRules.canResolve(it))
        }
    }

    @Test fun blank_remarks_are_refused() {
        assertEquals(ResolveRules.MSG_REMARKS_BLANK, ResolveRules.validateRemarks(""))
        assertEquals(ResolveRules.MSG_REMARKS_BLANK, ResolveRules.validateRemarks("   \n  "))
    }

    @Test fun very_short_remarks_are_refused() {
        assertEquals(ResolveRules.MSG_REMARKS_SHORT, ResolveRules.validateRemarks("Put out"))
        // Spaces around the text do not count toward the minimum.
        assertEquals(ResolveRules.MSG_REMARKS_SHORT, ResolveRules.validateRemarks("   Put out   "))
    }

    @Test fun remarks_at_the_limits_are_accepted() {
        assertNull(ResolveRules.validateRemarks("x".repeat(ResolveRules.MIN_REMARKS)))
        assertNull(ResolveRules.validateRemarks("x".repeat(ResolveRules.MAX_REMARKS)))
    }

    @Test fun remarks_over_the_maximum_are_refused() {
        assertEquals(
            ResolveRules.MSG_REMARKS_LONG,
            ResolveRules.validateRemarks("x".repeat(ResolveRules.MAX_REMARKS + 1))
        )
    }

    @Test fun saved_remarks_have_no_surrounding_whitespace() {
        assertEquals("Fire put out by BFP.", ResolveRules.cleanRemarks("  \nFire put out by BFP.\n "))
    }

    @Test fun resolving_is_not_a_plain_status_step() {
        // The status buttons stop at ON_SCENE; resolving goes through remarks instead.
        assertNull(IncidentActionRules.nextStatus(IncidentStatus.ON_SCENE))
        assertTrue(ResolveRules.canResolve(IncidentStatus.ON_SCENE))
    }

    // ---------- Round 2: quick notes and duration ----------

    @Test fun addNote_startsAndAppendsLines() {
        val one = ResolveRules.addNote("", "Fire put out")
        assertEquals("Fire put out.", one)
        assertEquals("Fire put out.\nNobody hurt.", ResolveRules.addNote(one, "Nobody hurt"))
    }

    @Test fun addNote_ignoresADuplicateAndAnOverflow() {
        val one = ResolveRules.addNote("", "Fire put out")
        assertEquals(one, ResolveRules.addNote(one, "Fire put out"))
        val long = "x".repeat(ResolveRules.MAX_REMARKS - 3)
        assertEquals(long, ResolveRules.addNote(long, "Nobody hurt"))
    }

    @Test fun hasNote_followsTheText() {
        val text = ResolveRules.addNote("Arrived 3:40 PM.", "Nobody hurt")
        assertTrue(ResolveRules.hasNote(text, "Nobody hurt"))
        assertFalse(ResolveRules.hasNote(text, "Fire put out"))
    }

    @Test fun duration_isReadable_andNeverNegative() {
        val min = 60_000L
        assertEquals("0 min", ResolveRules.durationLabel(10 * min, 5 * min))
        assertEquals("45 min", ResolveRules.durationLabel(0, 45 * min))
        assertEquals("1 h 12 min", ResolveRules.durationLabel(0, 72 * min))
        assertEquals("2 h", ResolveRules.durationLabel(0, 120 * min))
    }
}
