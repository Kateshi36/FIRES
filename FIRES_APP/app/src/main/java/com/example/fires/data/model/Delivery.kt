package com.example.fires.data.model

/**
 * Has a report reached the server yet? This is what the confirmation screen shows.
 * Not stored in Firestore: it is worked out from the phone's own view of the document.
 */
enum class DeliveryState {
    /** Saved on this phone, waiting for the server to confirm. Normal while offline. */
    SENDING,

    /** The server has the report. */
    SENT,

    /** The server refused the write, so the report is gone from the phone as well. */
    FAILED
}

/** A report as this phone sees it right now, plus whether the server has it yet. */
data class IncidentDelivery(val incident: Incident?, val state: DeliveryState)

/**
 * Turns Firestore's snapshot metadata into a [DeliveryState]. Returns null when we cannot tell yet.
 *
 *  - document exists, writes still pending  -> SENDING (queued on the phone)
 *  - document exists, nothing pending       -> SENT (the server confirmed it)
 *  - document missing, answer from server   -> FAILED (the write was refused and rolled back)
 *  - document missing, answer from cache    -> unknown: the phone just has not heard from the server
 */
fun deliveryStateOf(exists: Boolean, hasPendingWrites: Boolean, fromCache: Boolean): DeliveryState? =
    when {
        exists && hasPendingWrites -> DeliveryState.SENDING
        exists -> DeliveryState.SENT
        !fromCache -> DeliveryState.FAILED
        else -> null
    }
