package com.example.fires.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Open this incident as soon as possible": set when a notification is tapped (F1.9), used by the
 * navigation once the responder area is open (F1.10).
 *
 * A holder that lives for the whole app process, on purpose. When the app was closed, the tap
 * launches it and Splash runs first, then login routing. Anything stored in a screen would be
 * thrown away by those hops; this is not. Splash does not know it exists, so it cannot swallow it.
 *
 * Only the latest tap matters: a second tap replaces the first.
 */
object PendingAlertTarget {
    private val _incidentId = MutableStateFlow<String?>(null)
    val incidentId: StateFlow<String?> = _incidentId.asStateFlow()

    /** A blank id is ignored (nothing to open). */
    fun set(incidentId: String?) {
        if (!incidentId.isNullOrBlank()) _incidentId.value = incidentId
    }

    fun clear() {
        _incidentId.value = null
    }
}

/** Where the person is, as far as a pending alert target cares. */
enum class TargetPlace {
    /** Splash is still working out who this is (or nothing is on screen yet). */
    STARTING,

    /** Any screen of the responder area. */
    RESPONDER_AREA,

    /** Any screen of the citizen area (F2 notifications open here). */
    CITIZEN_AREA,

    /** Login, sign-up, permission, profile setup: nobody's area yet. */
    ELSEWHERE
}

enum class TargetAction { WAIT, OPEN, DROP }

object AlertTargetRules {

    /**
     * WAIT while Splash is still deciding: the target is kept, so it survives the trip through
     * splash and login routing. OPEN once the responder area is showing. DROP for anyone else
     * (a citizen, or a signed-out person, who ends up on permission/login): they must never be
     * sent to a responder screen, and the target must not linger and fire after a later login.
     */
    fun decide(place: TargetPlace): TargetAction = when (place) {
        TargetPlace.STARTING -> TargetAction.WAIT
        TargetPlace.RESPONDER_AREA -> TargetAction.OPEN
        TargetPlace.CITIZEN_AREA, TargetPlace.ELSEWHERE -> TargetAction.DROP
    }
}
