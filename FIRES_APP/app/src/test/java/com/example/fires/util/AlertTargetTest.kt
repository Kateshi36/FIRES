package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlertTargetTest {

    // The holder lives for the whole process, so every test starts from empty.
    @Before fun reset() = PendingAlertTarget.clear()

    // ---------- Holder ----------

    @Test fun startsEmpty() = assertNull(PendingAlertTarget.incidentId.value)

    @Test fun set_keepsTheId() {
        PendingAlertTarget.set("abc")
        assertEquals("abc", PendingAlertTarget.incidentId.value)
    }

    @Test fun set_blankOrNull_isIgnored() {
        PendingAlertTarget.set("   ")
        assertNull(PendingAlertTarget.incidentId.value)
        PendingAlertTarget.set(null)
        assertNull(PendingAlertTarget.incidentId.value)
    }

    @Test fun blankId_doesNotEraseARealOne() {
        PendingAlertTarget.set("abc")
        PendingAlertTarget.set("")
        assertEquals("abc", PendingAlertTarget.incidentId.value)
    }

    @Test fun secondTap_replacesTheFirst() {
        PendingAlertTarget.set("first")
        PendingAlertTarget.set("second")
        assertEquals("second", PendingAlertTarget.incidentId.value)
    }

    @Test fun clear_empties() {
        PendingAlertTarget.set("abc")
        PendingAlertTarget.clear()
        assertNull(PendingAlertTarget.incidentId.value)
    }

    // ---------- What to do with it ----------

    @Test fun whileSplashIsDeciding_waits_soTheTargetSurvivesTheTripThroughSplash() =
        assertEquals(TargetAction.WAIT, AlertTargetRules.decide(TargetPlace.STARTING))

    @Test fun inTheResponderArea_opens() =
        assertEquals(TargetAction.OPEN, AlertTargetRules.decide(TargetPlace.RESPONDER_AREA))

    @Test fun citizenOrSignedOut_isDropped() =
        assertEquals(TargetAction.DROP, AlertTargetRules.decide(TargetPlace.ELSEWHERE))
}
