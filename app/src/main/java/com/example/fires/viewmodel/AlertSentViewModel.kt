package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.IncidentDelivery
import com.example.fires.data.model.Severity
import com.example.fires.data.model.severityEnum
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.ui.common.LatLon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the "Alert sent" confirmation screen shows (D6). */
data class AlertSentUiState(
    /** Starts as SENDING: until the phone says otherwise, we do not claim the report was sent. */
    val delivery: DeliveryState = DeliveryState.SENDING,
    /** Where the report is. Null until the document is known (and after a FAILED send). */
    val location: LatLon? = null,
    val severity: Severity? = null,
    /** Another unresolved report was already sent from about the same place. */
    val isDuplicate: Boolean = false
)

/**
 * Watches one report right after it was sent. The screen follows it live: "Sending" while the
 * report is only saved on the phone (no signal), "Sent" the moment the server confirms, and
 * "Failed" if the server refused it.
 *
 * Only the duplicate flag is exposed. The suspicious flag is for responders and is never shown
 * to the person who sent the report.
 */
class AlertSentViewModel(
    incidentId: String,
    incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    val state: StateFlow<AlertSentUiState> = incidents.observeDelivery(incidentId)
        .map { it.toUiState() }
        // If the listener stops with an error, keep showing the last state instead of crashing.
        .catch { e -> Log.w(TAG, "Delivery listener stopped", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertSentUiState())

    companion object {
        private const val TAG = "AlertSentViewModel"

        /** The screen builds this ViewModel with the incident id from the navigation route. */
        fun factory(incidentId: String) = viewModelFactory {
            initializer { AlertSentViewModel(incidentId) }
        }
    }
}

private fun IncidentDelivery.toUiState() = AlertSentUiState(
    delivery = state,
    location = incident?.let { LatLon(it.latitude, it.longitude) },
    severity = incident?.severityEnum(),
    isDuplicate = incident?.isDuplicate == true
)
