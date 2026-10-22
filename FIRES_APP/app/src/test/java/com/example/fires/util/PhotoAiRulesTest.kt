package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoAiRulesTest {
    private fun incident(d: String?, s: String?, form: Severity = Severity.LOW, c: Double? = 0.85) =
        Incident(hasPhoto = true, severity = form.value, aiFireDetected = d, aiSeverity = s, aiConfidence = c)

    @Test fun hasAdviceOnlyWhenAnswerSaved() {
        assertTrue(PhotoAiRules.hasAdvice(incident("yes", "high")))
        assertFalse(PhotoAiRules.hasAdvice(incident(null, null)))
    }

    @Test fun suggestsOnlyWhenFireSeen() {
        assertEquals(Severity.HIGH, PhotoAiRules.suggestedSeverity(incident("yes", "high")))
        assertNull(PhotoAiRules.suggestedSeverity(incident("unsure", "high")))
        assertNull(PhotoAiRules.suggestedSeverity(incident("no", "none")))
        assertNull(PhotoAiRules.suggestedSeverity(incident(null, null)))
    }

    @Test fun mismatchAgainstFormScore() {
        assertTrue(PhotoAiRules.mismatch(incident("yes", "high", Severity.LOW)))
        assertFalse(PhotoAiRules.mismatch(incident("yes", "high", Severity.HIGH)))
        assertFalse(PhotoAiRules.mismatch(incident("no", "none", Severity.LOW)))
    }

    @Test fun wording() {
        assertEquals("Fire likely", PhotoAiRules.headline(incident("yes", "high")))
        assertEquals("No fire seen", PhotoAiRules.headline(incident("no", "none")))
        assertEquals("Not sure there is a fire", PhotoAiRules.headline(incident("unsure", "low")))
        assertEquals("High (85%)", PhotoAiRules.severityLine(incident("yes", "high")))
        assertEquals("", PhotoAiRules.severityLine(incident("no", "none")))
    }

    @Test fun looksGeneratedOnlyOnYes() {
        assertTrue(PhotoAiRules.looksGenerated(incident("yes", "high").apply { aiGenerated = "yes" }))
        assertFalse(PhotoAiRules.looksGenerated(incident("yes", "high").apply { aiGenerated = "unsure" }))
        assertFalse(PhotoAiRules.looksGenerated(incident("yes", "high")))
    }
}
