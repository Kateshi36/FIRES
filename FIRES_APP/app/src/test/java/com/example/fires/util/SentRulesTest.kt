package com.example.fires.util

import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentRulesTest {
    @Test fun sent_report_has_received_done_and_verification_next() {
        val steps = SentRules.steps(DeliveryState.SENT, null, hasPhoto = true)
        assertEquals(listOf(SentStepState.DONE, SentStepState.NOW, SentStepState.TODO, SentStepState.TODO), steps.map { it.state })
        assertEquals("Location and photo sent", steps[0].detail)
        assertEquals("Waiting to be verified", steps[1].title)
    }

    @Test fun the_screen_follows_the_report_status_like_the_tracking_screen() {
        fun states(s: IncidentStatus) = SentRules.steps(DeliveryState.SENT, null, true, s).map { it.state }
        assertEquals(listOf(SentStepState.DONE, SentStepState.DONE, SentStepState.NOW, SentStepState.TODO), states(IncidentStatus.VERIFIED))
        assertEquals(listOf(SentStepState.DONE, SentStepState.DONE, SentStepState.NOW, SentStepState.TODO), states(IncidentStatus.DISPATCHED))
        assertEquals(listOf(SentStepState.DONE, SentStepState.DONE, SentStepState.DONE, SentStepState.NOW), states(IncidentStatus.ON_SCENE))
        assertEquals(listOf(SentStepState.DONE, SentStepState.DONE, SentStepState.DONE, SentStepState.DONE), states(IncidentStatus.RESOLVED))
        assertEquals("Verified", SentRules.steps(DeliveryState.SENT, null, true, IncidentStatus.DISPATCHED)[1].title)
        assertEquals("Help is on the way", SentRules.title(DeliveryState.SENT, IncidentStatus.DISPATCHED))
        assertEquals("Report received", SentRules.title(DeliveryState.SENT, IncidentStatus.REPORTED))
        assertEquals("Reviewed and closed", SentRules.steps(DeliveryState.SENT, null, true, IncidentStatus.DISMISSED)[1].title)
    }

    @Test fun a_report_still_on_the_phone_is_not_called_received() {
        val steps = SentRules.steps(DeliveryState.SENDING, null, hasPhoto = false)
        assertEquals(SentStepState.NOW, steps[0].state)
        assertEquals(SentStepState.TODO, steps[1].state)
        assertEquals("Saving your report", SentRules.title(DeliveryState.SENDING))
        assertTrue(SentRules.subtitle(DeliveryState.SENDING).contains("saved on this phone"))
    }

    @Test fun chip_and_failure_wording() {
        assertEquals("INC-1042 · House fire", SentRules.chip("INC-1042", "House fire"))
        assertEquals("INC-1042", SentRules.chip("INC-1042", ""))
        assertNull(SentRules.chip("", "House fire"))
        assertEquals("Report not sent", SentRules.title(DeliveryState.FAILED))
    }

    @Test fun no_hotline_anywhere() {
        val all = DeliveryState.entries.flatMap { listOf(SentRules.title(it), SentRules.subtitle(it)) } + SentRules.KEEP_PHONE_NEARBY
        assertTrue(all.none { it.contains("911") || it.contains("call", ignoreCase = true) })
    }
}
