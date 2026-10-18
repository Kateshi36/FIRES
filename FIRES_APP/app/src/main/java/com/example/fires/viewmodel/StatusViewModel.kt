package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.ResponderLocation
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.ResponderLocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What the citizen's "Report status" screen shows (D7). */
data class StatusUiState(
    /** Null until the first answer arrives, and after a FAILED send. */
    val incident: Incident? = null,
    val delivery: DeliveryState = DeliveryState.SENDING,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** H4: responders sharing a location right now. Only filled while the incident is DISPATCHED. */
    val responders: List<ResponderLocation> = emptyList(),
    /** H4: the phone's clock, refreshed every few seconds so "last updated" ages even when nothing arrives. */
    val nowMillis: Long = 0L
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
    incidents: IncidentRepository = IncidentRepository(),
    locations: ResponderLocationRepository = ResponderLocationRepository()
) : ViewModel() {

    /** Goes up by one on each retry, which restarts the listener below. */
    private val restarts = MutableStateFlow(0)

    /** The responders and the clock (H4). */
    private data class Tracking(val responders: List<ResponderLocation>, val nowMillis: Long)

    private val report: Flow<StatusUiState> = restarts
        .flatMapLatest {
            incidents.observeDelivery(incidentId)
                .map { StatusUiState(it.incident, it.state, isLoading = false) }
                .onStart { emit(StatusUiState()) }
                .catch { e ->
                    Log.w(TAG, "Status listener stopped", e)
                    emit(StatusUiState(isLoading = false, error = LOAD_ERROR))
                }
        }
        // Shared, because two flows below read it. Without this each would start its own listener.
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Listens to the responders' locations ONLY while the incident is DISPATCHED: before that nobody
     * is sharing, and after it the documents are gone. Keyed on the status (not the whole report),
     * so an unrelated change to the report does not restart the listener. A failed listener (for
     * example the rules refuse it) just means nobody is shown, never a broken screen.
     */
    private val tracking: Flow<Tracking> = report
        .map { it.incident?.statusEnum() == IncidentStatus.DISPATCHED }
        .distinctUntilChanged()
        .flatMapLatest { dispatched ->
            if (!dispatched) flowOf(Tracking(emptyList(), 0L))
            else combine(
                locations.observe(incidentId)
                    .catch { e ->
                        Log.w(TAG, "Responder location listener stopped", e)
                        emit(emptyList())
                    },
                clock()
            ) { responders, now -> Tracking(responders, now) }
        }

    val state: StateFlow<StatusUiState> = combine(report, tracking) { current, track ->
        current.copy(responders = track.responders, nowMillis = track.nowMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusUiState())

    /** The current time, again every few seconds. */
    private fun clock(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MS)
        }
    }

    fun retry() {
        restarts.value += 1
    }

    companion object {
        private const val TAG = "StatusViewModel"
        private const val CLOCK_TICK_MS = 5_000L
        const val LOAD_ERROR = "We couldn't load this report. Check your connection and try again."

        fun factory(incidentId: String) = viewModelFactory {
            initializer { StatusViewModel(incidentId) }
        }
    }
}
