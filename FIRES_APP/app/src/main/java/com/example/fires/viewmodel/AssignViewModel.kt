package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.Assignment
import com.example.fires.data.model.Incident
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AssignmentRepository
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.AssignmentRules
import com.example.fires.util.IncidentActionRules
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

/** What the "Assign responders / resources" screen shows (E4). */
data class AssignUiState(
    /** The report being assigned to. Null while loading, and when it does not exist. */
    val incident: Incident? = null,
    val isLoading: Boolean = true,
    val unit: String? = null,
    val resources: Set<String> = emptySet(),
    val unitError: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    /** Only open reports can be assigned to. */
    val canAssign: Boolean get() = incident != null && IncidentActionRules.canAssign(incident.statusEnum())
}

/**
 * Writes one assignment (unit + resources) to incidents/{id}/assignments. The signed-in responder
 * is recorded as the person who made it.
 *
 * Saving waits for the server (with a time limit) so the responder knows it went through. The
 * assignment id is created once per screen, so if the wait times out and the responder taps Assign
 * again, the second save overwrites the first instead of adding a duplicate.
 */
class AssignViewModel(
    private val incidentId: String,
    incidents: IncidentRepository = IncidentRepository(),
    private val assignments: AssignmentRepository = AssignmentRepository(),
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private data class Form(
        val unit: String? = null,
        val resources: Set<String> = emptySet(),
        val unitError: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null
    )

    private data class Snapshot(val incident: Incident?, val loading: Boolean)

    private val form = MutableStateFlow(Form())
    private val assignmentId = assignments.newId(incidentId)

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

    val state: StateFlow<AssignUiState> = combine(incident, form) { snapshot, f ->
        AssignUiState(
            incident = snapshot.incident,
            isLoading = snapshot.loading,
            unit = f.unit,
            resources = f.resources,
            unitError = f.unitError,
            isSaving = f.isSaving,
            error = f.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssignUiState())

    fun onUnitSelect(unit: String) = edit { it.copy(unit = unit, unitError = null, error = null) }

    fun onResourceToggle(resource: String) = edit {
        it.copy(resources = AssignmentRules.toggle(it.resources, resource), error = null)
    }

    // The choices are frozen while a save is running, so what was sent is what was chosen.
    private fun edit(change: (Form) -> Form) = form.update { if (it.isSaving) it else change(it) }

    fun onAssignClick() {
        val f = form.value
        if (f.isSaving) return // ignore double taps

        val status = state.value.incident?.statusEnum()
        if (status == null || !IncidentActionRules.canAssign(status)) {
            form.update { it.copy(error = AssignmentRules.MSG_CLOSED) }
            return
        }

        val unitError = AssignmentRules.validateUnit(f.unit)
        if (unitError != null || f.unit == null) {
            form.update { it.copy(unitError = unitError ?: AssignmentRules.MSG_UNIT_REQUIRED) }
            return
        }

        val uid = auth.currentUid
        if (uid == null) {
            form.update { it.copy(error = AssignmentRules.MSG_SIGNED_OUT) }
            return
        }

        form.update { it.copy(isSaving = true, unitError = null, error = null) }

        viewModelScope.launch {
            // Name for the list. Falls back to the e-mail, then a plain word, so a slow or failed
            // read never blocks the assignment itself.
            val name = attempt { withTimeout(NAME_TIMEOUT_MS) { users.getUser(uid) } }
                .getOrNull()?.fullName?.takeIf { it.isNotBlank() }
                ?: auth.currentEmail
                ?: "Responder"

            val assignment = Assignment(
                responderId = uid,
                responderName = name,
                unit = f.unit,
                assignedResources = AssignmentRules.orderedResources(f.resources)
            )

            val result = attempt {
                withTimeout(WRITE_TIMEOUT_MS) {
                    assignments.save(incidentId, assignmentId, assignment).await()
                }
            }

            if (result.isSuccess) {
                form.update { it.copy(isSaving = false) }
                _done.send(Unit)
            } else {
                Log.w(TAG, "Could not save the assignment for $incidentId", result.exceptionOrNull())
                form.update { it.copy(isSaving = false, error = AssignmentRules.MSG_NOT_CONFIRMED) }
            }
        }
    }

    companion object {
        private const val TAG = "AssignViewModel"
        private const val NAME_TIMEOUT_MS = 4_000L
        private const val WRITE_TIMEOUT_MS = 10_000L

        fun factory(incidentId: String) = viewModelFactory {
            initializer { AssignViewModel(incidentId) }
        }
    }
}
