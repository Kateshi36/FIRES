package com.example.fires.service

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The latest route for one incident, and when it was worked out. */
data class LiveRoute(val incidentId: String, val route: Route, val computedAtMillis: Long)

/**
 * What the location service knows right now, for the incident detail screen to show: which
 * incident this phone is sharing for (or null), where the responder is, and the current route.
 * The service writes it and the screen reads it. It lives in the app process, the same place the
 * service runs, so a plain object is enough.
 */
object LocationShareState {
    private val _activeIncidentId = MutableStateFlow<String?>(null)
    val activeIncidentId: StateFlow<String?> = _activeIncidentId.asStateFlow()

    private val _position = MutableStateFlow<LatLon?>(null)
    /** The responder's latest position, updated on every GPS fix (even ones not written to Firestore). */
    val position: StateFlow<LatLon?> = _position.asStateFlow()

    private val _route = MutableStateFlow<LiveRoute?>(null)
    val route: StateFlow<LiveRoute?> = _route.asStateFlow()

    private val _gpsLost = MutableStateFlow(false)
    /**
     * True while the phone's GPS is switched off during a response (H5b). Set from the location
     * callback, so the screen hears about it at once instead of only when it comes back into view.
     */
    val gpsLost: StateFlow<Boolean> = _gpsLost.asStateFlow()

    internal fun set(incidentId: String?) {
        _gpsLost.value = false // a new response, or the end of one, always starts clean
        _activeIncidentId.value = incidentId
        if (incidentId == null) {
            _position.value = null
            _route.value = null
        }
    }

    internal fun setPosition(position: LatLon) {
        _position.value = position
    }

    internal fun setGpsLost(lost: Boolean) {
        _gpsLost.value = lost
    }

    internal fun setRoute(route: LiveRoute?) {
        _route.value = route
    }
}
