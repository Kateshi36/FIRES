package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.Incident
import com.example.fires.data.repository.IncidentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What the citizen's "Report status" screen shows (D7). */
data class StatusUiState(
    /** Null until the first answer arrives, and after a FAILED send. */
    val incident: Incident? = null,
    val delivery: DeliveryState = DeliveryState.SENDING,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Follows one report live. A Firestore listener pushes every change, so when a responder verifies,
 * dispatches or resolves the report the stepper moves by itself, with no refresh button.
 *
 * Offline the listener keeps answering from the phone's saved copy, so the last known status stays on
 * screen. If the listener itself fails (for example the server refuses it), [retry] starts it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatusViewModel(
    incidentId: String,
    incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    /** Goes up by one on each retry, which restarts the listener below. */
    private val restarts = MutableStateFlow(0)

    val state: StateFlow<StatusUiState> = restarts
        .flatMapLatest {
            incidents.observeDelivery(incidentId)
                .map { StatusUiState(it.incident, it.state, isLoading = false) }
                .onStart { emit(StatusUiState()) }
                .catch { e ->
                    Log.w(TAG, "Status listener stopped", e)
                    emit(StatusUiState(isLoading = false, error = LOAD_ERROR))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState())

    fun retry() {
        restarts.value += 1
    }

    companion object {
        private const val TAG = "StatusViewModel"
        const val LOAD_ERROR = "We couldn't load this report. Check your connection and try again."

        fun factory(incidentId: String) = viewModelFactory {
            initializer { StatusViewModel(incidentId) }
        }
    }
}
