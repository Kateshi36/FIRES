package com.example.fires.util

import com.example.fires.data.model.DeliveryState

/** One row of the "What happens next" list on the report-sent screen. */
data class SentStep(val title: String, val detail: String, val state: SentStepState)

enum class SentStepState { DONE, NOW, TODO }

/**
 * The words of the report-sent screen (round 2 design). It never claims more than is true: while
 * the report is only saved on the phone, "Received" is not done yet.
 */
object SentRules {

    fun title(delivery: DeliveryState): String = when (delivery) {
        DeliveryState.SENDING -> "Saving your report"
        DeliveryState.SENT -> "Report received"
        DeliveryState.FAILED -> "Report not sent"
    }

    fun subtitle(delivery: DeliveryState): String = when (delivery) {
        DeliveryState.SENDING ->
            "It is saved on this phone and goes out by itself as soon as you have a connection."
        DeliveryState.SENT -> "B-FLARE and the barangay have your location. Stay somewhere safe."
        DeliveryState.FAILED -> "We couldn't deliver your report. Please go back and send it again."
    }

    /** "INC-1042 · House fire", or null until the report is known. */
    fun chip(code: String, fireType: String): String? =
        if (code.isBlank()) null else if (fireType.isBlank()) code else "$code · $fireType"

    fun steps(delivery: DeliveryState, sentAtMillis: Long?, hasPhoto: Boolean): List<SentStep> {
        val sent = delivery == DeliveryState.SENT
        val what = if (hasPhoto) "location and photo sent" else "location sent"
        val receivedDetail = when {
            !sent -> "Waiting for a connection"
            sentAtMillis != null -> MyReportsRules.timeLabel(sentAtMillis) + " · " + what
            else -> what.replaceFirstChar { it.uppercase() }
        }
        return listOf(
            SentStep("Received", receivedDetail, if (sent) SentStepState.DONE else SentStepState.NOW),
            SentStep(
                "Waiting to be verified", "An administrator checks the report",
                if (sent) SentStepState.NOW else SentStepState.TODO
            ),
            SentStep("Responders dispatched", "You will see them on the map", SentStepState.TODO),
            SentStep("On scene", "", SentStepState.TODO)
        )
    }

    const val KEEP_PHONE_NEARBY =
        "Keep your phone nearby. A responder may message you, and we will notify you at each step."
}
