package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentRecord
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.RecordRepository
import com.example.fires.util.ResolveRules
import com.example.fires.util.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** What the "Resolve incident" screen shows (E5). */
data class ResolveUiState(
    /** The report being resolved. Null while loading, and when it does not exist. */
    val incident: Incident? = null,
    val isLoading: Boolean = true,
    val remarks: String = "",
    val remarksError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    /**
     * True once a save was started. Firestore applies a write on the phone at once, so after a
     * timed-out attempt the report already reads RESOLVED locally even though the server has not
     * confirmed it. This keeps the button usable so the person can confirm by tapping again.
     */
    val attempted: Boolean = false
) {
    val canResolve: Boolean
        get() = incident != null && (attempted || ResolveRules.canResolve(incident.statusEnum()))
}

/**
 * Resolves a report: writes records/{incidentId} with the remarks and sets the report to RESOLVED,
 * in one batch (see RecordRepository.resolve). The signed-in responder is recorded as resolvedBy.
 *
 * Saving waits for the server (with a time limit) so the responder knows it went through. The
 * record id is the incident id, so tapping Resolve again after a timeout overwrites the same
 * record instead of adding a second one.
 */
class ResolveViewModel(
    private val incidentId: String,
    incidents: IncidentRepository = IncidentRepository(),
    private val records: RecordRepository = RecordRepository(),
    private val auth: AuthRepository = AuthRepository()
) : ViewModel() {

    private data class Form(
        val remarks: String = "",
        val remarksError: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null,
        val attempted: Boolean = false
    )

    private data class Snapshot(val incident: Incident?, val loading: Boolean)

    private val form = MutableStateFlow(Form())

    // One-time "saved, go back" signal, so a screen rotation cannot replay it.
    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    private val incident: Flow<Snapshot> = incidents.observe(incidentId)
        .map { Snapshot(it, loading = false) }
        .onStart { emit(Snapshot(null, loading = true)) }
        .catch { e ->
            Log.w(TAG, "Incident listener stopped", e)
            emit(Snapshot(null, loading = false))
        }

    val state: StateFlow<ResolveUiState> = combine(incident, form) { snapshot, f ->
        ResolveUiState(
            incident = snapshot.incident,
            isLoading = snapshot.loading,
            remarks = f.remarks,
            remarksError = f.remarksError,
            isSaving = f.isSaving,
            error = f.error,
            attempted = f.attempted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResolveUiState())

    // Frozen while a save is running, so what was sent is what was typed.
    fun onRemarksChange(value: String) = form.update {
        if (it.isSaving) it else it.copy(remarks = value, remarksError = null, error = null)
    }

    fun onResolveClick() {
        val f = form.value
        if (f.isSaving) return // ignore double taps

        // Check the LIVE report, so a stale tap (another responder already acted) does nothing.
        // After our own first attempt the local copy already says RESOLVED, so skip the check.
        val status = state.value.incident?.statusEnum()
        if (!f.attempted && (status == null || !ResolveRules.canResolve(status))) {
            form.update { it.copy(error = ResolveRules.MSG_NOT_ON_SCENE) }
            return
        }

        val remarksError = ResolveRules.validateRemarks(f.remarks)
        if (remarksError != null) {
            form.update { it.copy(remarksError = remarksError) }
            return
        }

        val uid = auth.currentUid
        if (uid == null) {
            form.update { it.copy(error = ResolveRules.MSG_SIGNED_OUT) }
            return
        }

        form.update { it.copy(isSaving = true, attempted = true, remarksError = null, error = null) }

        val record = IncidentRecord(
            incidentId = incidentId,
            resolvedBy = uid,
            remarks = ResolveRules.cleanRemarks(f.remarks)
            // dateResolved is filled in by the server (@ServerTimestamp).
        )

        viewModelScope.launch {
            val result = attempt { withTimeout(WRITE_TIMEOUT_MS) { records.resolve(record).await() } }

            if (result.isSuccess) {
                form.update { it.copy(isSaving = false) }
                _done.send(Unit)
            } else {
                Log.w(TAG, "Could not resolve $incidentId", result.exceptionOrNull())
                form.update { it.copy(isSaving = false, error = ResolveRules.MSG_NOT_CONFIRMED) }
            }
        }
    }

    companion object {
        private const val TAG = "ResolveViewModel"
        private const val WRITE_TIMEOUT_MS = 10_000L

        fun factory(incidentId: String) = viewModelFactory {
            initializer { ResolveViewModel(incidentId) }
        }
    }
}
