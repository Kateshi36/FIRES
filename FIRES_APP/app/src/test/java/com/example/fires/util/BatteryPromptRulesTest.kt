package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1.13: when the battery question is asked, and when the menu item shows. */
class BatteryPromptRulesTest {

    @Test fun firstVisit_notAllowedYet_asks() =
        assertTrue(BatteryPromptRules.shouldAskOnFirstVisit(isIgnoringOptimizations = false, alreadyAsked = false))

    @Test fun alreadyAsked_doesNotAskAgain() =
        assertFalse(BatteryPromptRules.shouldAskOnFirstVisit(isIgnoringOptimizations = false, alreadyAsked = true))

    @Test fun alreadyAllowed_neverAsks() {
        assertFalse(BatteryPromptRules.shouldAskOnFirstVisit(isIgnoringOptimizations = true, alreadyAsked = false))
        assertFalse(BatteryPromptRules.shouldAskOnFirstVisit(isIgnoringOptimizations = true, alreadyAsked = true))
    }

    @Test fun menuItem_showsOnlyWhileTheAppCanStillBeOptimized() {
        assertTrue(BatteryPromptRules.showMenuItem(isIgnoringOptimizations = false))
        assertFalse(BatteryPromptRules.showMenuItem(isIgnoringOptimizations = true))
    }

    // ---- H5e: the first "Start response" ----

    @Test fun startResponse_firstTime_optimizationOn_asks() =
        assertTrue(BatteryPromptRules.shouldAskAtFirstStartResponse(isIgnoringOptimizations = false, alreadyAskedForResponse = false))

    @Test fun startResponse_alreadyAsked_doesNotAskAgain() =
        assertFalse(BatteryPromptRules.shouldAskAtFirstStartResponse(isIgnoringOptimizations = false, alreadyAskedForResponse = true))

    @Test fun startResponse_alreadyAllowed_neverAsks() {
        assertFalse(BatteryPromptRules.shouldAskAtFirstStartResponse(isIgnoringOptimizations = true, alreadyAskedForResponse = false))
        assertFalse(BatteryPromptRules.shouldAskAtFirstStartResponse(isIgnoringOptimizations = true, alreadyAskedForResponse = true))
    }

    @Test fun dashboardQuestionAndStartResponseQuestionAreIndependent() {
        // The dashboard question was already asked (its flag is true). The Start response question
        // looks only at its own flag, so it still asks the first time.
        val dashboardAsked = true
        assertFalse(BatteryPromptRules.shouldAskOnFirstVisit(isIgnoringOptimizations = false, alreadyAsked = dashboardAsked))
        assertTrue(BatteryPromptRules.shouldAskAtFirstStartResponse(isIgnoringOptimizations = false, alreadyAskedForResponse = false))
    }

    // ---- dialog wording ----

    @Test fun locationDialogHasItsOwnTitleAndMessage() {
        assertEquals("Keep location sharing running", BatteryPromptRules.LOCATION_TITLE)
        assertTrue(BatteryPromptRules.LOCATION_MESSAGE.contains("location"))
        assertNotEquals(BatteryPromptRules.ALERTS_MESSAGE, BatteryPromptRules.LOCATION_MESSAGE)
    }

    @Test fun defaultWordingIsTheOriginalAlertsText() {
        assertEquals("Keep alerts on time", BatteryPromptRules.ALERTS_TITLE)
        assertTrue(BatteryPromptRules.ALERTS_MESSAGE.startsWith("To save battery, Android can put this app to sleep, and then fire alerts may arrive late."))
    }
}
