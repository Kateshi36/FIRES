package com.example.fires.util

import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.IncidentStatus

/** One row of the "What happens next" list on the report-sent screen. */
data class SentStep(val title: String, val detail: String, val state: SentStepState)

enum class SentStepState { DONE, NOW, TODO }

/**
 * The words of the report-sent screen (round 2 design). It never claims more than is true: while
 * the report is only saved on the phone, "Received" is not done yet.
 */
object SentRules {

    /**
     * Once the report has moved on (verified, dispatched, ...) this screen says the same as the Track live status
     * screen, so the two never disagree. [status] is the report's current status.
     */
    fun title(delivery: DeliveryState, status: IncidentStatus = IncidentStatus.REPORTED): String = when (delivery) {
        DeliveryState.SENDING -> "Saving your report"
        DeliveryState.SENT -> if (status == IncidentStatus.REPORTED) "Report received" else statusHeadline(status)
        DeliveryState.FAILED -> "Report not sent"
    }

    fun subtitle(delivery: DeliveryState, status: IncidentStatus = IncidentStatus.REPORTED): String = when (delivery) {
        DeliveryState.SENDING ->
            "It is saved on this phone and goes out by itself as soon as you have a connection."
        DeliveryState.SENT ->
            if (status == IncidentStatus.REPORTED) "B-FLARE and the barangay have your location. Stay somewhere safe."
            else statusDetail(status)
        DeliveryState.FAILED -> "We couldn't deliver your report. Please go back and send it again."
    }

    /** "INC-1042 · House fire", or null until the report is known. */
    fun chip(code: String, fireType: String): String? =
        if (code.isBlank()) null else if (fireType.isBlank()) code else "$code · $fireType"

    fun steps(
        delivery: DeliveryState, sentAtMillis: Long?, hasPhoto: Boolean,
        status: IncidentStatus = IncidentStatus.REPORTED
    ): List<SentStep> {
        val sent = delivery == DeliveryState.SENT
        val what = if (hasPhoto) "location and photo sent" else "location sent"
        val receivedDetail = when {
            !sent -> "Waiting for a connection"
            sentAtMillis != null -> MyReportsRules.timeLabel(sentAtMillis) + " · " + what
            else -> what.replaceFirstChar { it.uppercase() }
        }
        val received = SentStep("Received", receivedDetail, if (sent) SentStepState.DONE else SentStepState.NOW)
        if (!sent) {
            return listOf(
                received,
                SentStep("Waiting to be verified", "An administrator checks the report", SentStepState.TODO),
                SentStep("Responders dispatched", "You will see them on the map", SentStepState.TODO),
                SentStep("On scene", "", SentStepState.TODO)
            )
        }
        // A report that was reviewed and closed (false or duplicate) never reaches the responders.
        if (status == IncidentStatus.DISMISSED) {
            return listOf(
                received,
                SentStep("Reviewed and closed", "Responders reviewed this report", SentStepState.DONE),
                SentStep("Responders dispatched", "", SentStepState.TODO),
                SentStep("On scene", "", SentStepState.TODO)
            )
        }
        // How far the report has got: 0 reported, 1 verified, 2 dispatched, 3 on scene, 4 resolved.
        val level = when (status) {
            IncidentStatus.REPORTED -> 0
            IncidentStatus.VERIFIED -> 1
            IncidentStatus.DISPATCHED -> 2
            IncidentStatus.ON_SCENE -> 3
            IncidentStatus.RESOLVED -> 4
            IncidentStatus.DISMISSED -> 0
        }
        fun at(step: Int) = when {
            level > step -> SentStepState.DONE
            level == step -> SentStepState.NOW
            else -> SentStepState.TODO
        }
        return listOf(
            received,
            if (level >= 1) SentStep("Verified", "An administrator checked the report", SentStepState.DONE)
            else SentStep("Waiting to be verified", "An administrator checks the report", SentStepState.NOW),
            SentStep("Responders dispatched", "You will see them on the map", at(2).let { if (level == 1) SentStepState.NOW else it }),
            SentStep("On scene", "", at(3))
        )
    }

    const val KEEP_PHONE_NEARBY =
        "Keep your phone nearby. A responder may message you, and we will notify you at each step."
}
