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
}
