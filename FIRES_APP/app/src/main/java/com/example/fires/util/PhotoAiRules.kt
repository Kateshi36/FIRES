package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.Severity
import com.example.fires.data.model.severityEnum

/**
 * The AI photo advice (Phase AI), as the app shows it. The ADMINISTRATOR's website sends the photo to
 * the AI helper (the Cloudflare Worker) and saves six ai* fields on the report; the app only reads and
 * shows them. Pure Kotlin (no Firebase), so it runs as a plain JVM test. The AI only ADVISES: a
 * responder or the administrator decides, and the form-based severity stays as it is.
 *
 * The prompt below is a copy of the one in FIRES_AI_WORKER/src/core.js (the Worker's test checks that
 * the two are identical). Change it only by raising the version in both places.
 */
object PhotoAiRules {

    const val PROMPT_VERSION = "v2"

    // PROMPT-BEGIN
    const val PROMPT = """You are helping a barangay fire-response team in the Philippines. You are looking at ONE photo that a citizen attached to a fire report.
Answer only from what is visible in the photo. Do not guess about things you cannot see. You only ADVISE; a human decides.

Decide:
1. fireDetected: "yes" if flames are visible, "no" if there is clearly no fire or smoke, "unsure" if you cannot tell.
   Look-alikes that are NOT fire: sunsets, red lights, cooking flames in a stove, a lit candle or bonfire that is clearly controlled, fog or dust.
2. smokeOnly: true if there is smoke but no flames are visible, otherwise false.
3. severity, using this rubric:
   - "none": no fire.
   - "low": a small, contained fire (for example burning rubbish) with nothing nearby that can catch fire.
   - "medium": flames of moderate size, or a small fire close to houses or vehicles.
   - "high": a large fire, or flames on a building or vehicle, or heavy smoke that is spreading.
   - "critical": a building or several buildings heavily on fire, flames reaching other houses, or an explosion or a large fire with fuel or gas tanks nearby.
   When the photo is unclear, choose the lower level and say it is unclear.
4. confidence: a number from 0 to 1 for how sure you are.
5. reason: one plain sentence, under 200 characters, saying what you can see.
6. aiGenerated: "yes" if the picture looks computer-generated or digitally edited (for example an unnatural or too-perfect look, garbled text or objects, impossible flames or shapes), "no" if it looks like an ordinary camera photo, "unsure" if you cannot tell. This is only a hint and is separate from the fire question. Most real phone photos are "no".

Answer with JSON only, exactly in this shape:
{"fireDetected":"yes|no|unsure","smokeOnly":true|false,"severity":"none|low|medium|high|critical","confidence":0.0,"reason":"...","aiGenerated":"yes|no|unsure"}"""
    // PROMPT-END

    /** True when the incident already carries an AI answer. */
    fun hasAdvice(incident: Incident): Boolean = !incident.aiFireDetected.isNullOrEmpty()

    /** The severity the AI suggests, or null when it saw no fire or was not sure there was one. */
    fun suggestedSeverity(incident: Incident): Severity? {
        if (incident.aiFireDetected != "yes") return null
        return when (incident.aiSeverity) {
            "low" -> Severity.LOW
            "medium" -> Severity.MEDIUM
            "high" -> Severity.HIGH
            "critical" -> Severity.CRITICAL
            else -> null
        }
    }

    /** True when the AI and the form-based score disagree, so the person should look twice. */
    fun mismatch(incident: Incident): Boolean {
        val suggested = suggestedSeverity(incident) ?: return false
        return suggested != incident.severityEnum()
    }

    /** True when the AI thinks the photo itself looks computer-generated or edited. Only a hint. */
    fun looksGenerated(incident: Incident): Boolean = incident.aiGenerated == "yes"

    fun headline(incident: Incident): String = when (incident.aiFireDetected) {
        "yes" -> "Fire likely"
        "no" -> "No fire seen"
        "unsure" -> "Not sure there is a fire"
        else -> ""
    }

    /** "High (85%)" or "". */
    fun severityLine(incident: Incident): String {
        val level = suggestedSeverity(incident) ?: return ""
        val pct = Math.round((incident.aiConfidence ?: 0.0) * 100).toInt()
        return "${level.label} ($pct%)"
    }

    const val GENERATED_NOTE = "This photo may be AI-generated or edited. Look at it carefully."
    const val ADVICE_NOTE = "Advice only. You decide."
    const val CONSENT = "The photo is also read by an AI service that only gives advice to the " +
        "administrator and the responders. A person decides."
    const val NOT_CHECKED = "Not checked yet. The administrator's screen reads new photos with the AI."
}
