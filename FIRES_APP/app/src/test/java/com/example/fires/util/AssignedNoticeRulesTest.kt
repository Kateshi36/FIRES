package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignedNoticeRulesTest {
    private val now = 1_000_000_000L

    @Test fun a_fresh_notice_alerts_once() {
        assertTrue(AssignedNoticeRules.shouldAlert(now - 5_000, now, alreadyShown = false))
        assertFalse(AssignedNoticeRules.shouldAlert(now - 5_000, now, alreadyShown = true))
    }

    @Test fun old_or_unstamped_notices_do_not_alert() {
        assertFalse(AssignedNoticeRules.shouldAlert(now - AssignedNoticeRules.MAX_AGE_MS - 1, now, false))
        assertFalse(AssignedNoticeRules.shouldAlert(null, now, false))
    }

    @Test fun blank_text_gets_a_default() {
        assertEquals("Hello", AssignedNoticeRules.text(" Hello "))
        assertTrue(AssignedNoticeRules.text(null).contains("assigned"))
        assertTrue(AssignedNoticeRules.text("  ").contains("assigned"))
    }

    @Test fun a_removal_has_its_own_title_and_default_text() {
        assertEquals("Assignment removed", AssignedNoticeRules.title(true))
        assertEquals("You've been assigned", AssignedNoticeRules.title(false))
        assertTrue(AssignedNoticeRules.text(null, removed = true).contains("off"))
    }
}
