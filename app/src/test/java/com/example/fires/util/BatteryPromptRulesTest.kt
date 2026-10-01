package com.example.fires.util

import org.junit.Assert.assertFalse
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
}
