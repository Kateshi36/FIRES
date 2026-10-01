package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentActionRulesTest {

    @Test fun review_is_only_offered_on_a_reported_report() {
        assertTrue(IncidentActionRules.canReview(IncidentStatus.REPORTED))
        IncidentStatus.entries.filter { it != IncidentStatus.REPORTED }.forEach {
            assertFalse("$it", IncidentActionRules.canReview(it))
        }
    }

    @Test fun severity_and_assign_are_only_for_open_reports() {
        IncidentStatus.entries.forEach {
            assertEquals("$it", it.isActive, IncidentActionRules.canOverrideSeverity(it))
            assertEquals("$it", it.isActive, IncidentActionRules.canAssign(it))
        }
    }

    @Test fun status_moves_forward_one_step_after_verification() {
        assertEquals(IncidentStatus.DISPATCHED, IncidentActionRules.nextStatus(IncidentStatus.VERIFIED))
        assertEquals(IncidentStatus.ON_SCENE, IncidentActionRules.nextStatus(IncidentStatus.DISPATCHED))
    }

    @Test fun a_report_cannot_skip_verification_or_resolve_without_remarks() {
        assertNull(IncidentActionRules.nextStatus(IncidentStatus.REPORTED)) // must be verified first
        assertNull(IncidentActionRules.nextStatus(IncidentStatus.ON_SCENE)) // resolving needs remarks (ResolveRules)
        assertNull(IncidentActionRules.nextStatus(IncidentStatus.RESOLVED))
        assertNull(IncidentActionRules.nextStatus(IncidentStatus.DISMISSED))
    }

    @Test fun undo_goes_back_one_step_but_never_to_reported() {
        assertEquals(IncidentStatus.DISPATCHED, IncidentActionRules.previousStatus(IncidentStatus.ON_SCENE))
        assertEquals(IncidentStatus.VERIFIED, IncidentActionRules.previousStatus(IncidentStatus.DISPATCHED))
        assertNull(IncidentActionRules.previousStatus(IncidentStatus.VERIFIED))
        assertNull(IncidentActionRules.previousStatus(IncidentStatus.REPORTED))
        assertNull(IncidentActionRules.previousStatus(IncidentStatus.RESOLVED))
    }

    @Test fun only_false_and_duplicate_ask_for_confirmation() {
        assertTrue(IncidentActionRules.needsConfirmation(Verification.FALSE))
        assertTrue(IncidentActionRules.needsConfirmation(Verification.DUPLICATE))
        assertFalse(IncidentActionRules.needsConfirmation(Verification.VERIFIED))
        assertFalse(IncidentActionRules.needsConfirmation(Verification.PENDING))
    }

    @Test fun confirm_texts_say_what_will_happen() {
        assertEquals("Mark as false report?", IncidentActionRules.confirmTitle(Verification.FALSE))
        assertEquals("Mark as duplicate?", IncidentActionRules.confirmTitle(Verification.DUPLICATE))
        assertTrue(IncidentActionRules.confirmBody(Verification.FALSE).contains("leave the dashboard"))
        assertEquals("Mark as false", IncidentActionRules.confirmButton(Verification.FALSE))
    }
}
