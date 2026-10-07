package com.example.fires.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.Assignment
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.Verification
import com.example.fires.data.model.severityEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AssignmentRepository
import com.example.fires.data.repository.AuthRepository
import com.example.fires.service.LiveRoute
import com.example.fires.service.LocationShareState
import com.example.fires.ui.common.LatLon
import com.example.fires.util.LocationShareRules
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.util.IncidentActionRules
import com.example.fires.util.IncidentDetailRules
import com.example.fires.util.PhotoProcessor
import com.example.fires.util.attempt
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The photo box on the detail screen. The photo is a separate document, so it loads on its own. */
sealed interface PhotoState {
    /** The reporter did not attach a photo. The screen shows no photo section. */
    data object None : PhotoState
    data object Loading : PhotoState
    data class Loaded(val bitmap: Bitmap) : PhotoState

    /** Not there yet (the photo is sent after the report), could not be read, or could not be shown. */
    data object Unavailable : PhotoState
}

/** What the responder's Incident detail screen shows (E2, E3, E4). */
data class IncidentDetailUiState(
    /** Null while loading, and when no report with this id exists. */
    val incident: Incident? = null,
    /** Reports grouped under this one, oldest first. */
    val duplicates: List<Incident> = emptyList(),
    /** When this report is itself a duplicate: the report it was grouped under, if it could be read. */
    val primary: Incident? = null,
    val photo: PhotoState = PhotoState.None,
    /** Units and resources assigned so far, oldest first. */
    val assignments: List<Assignment> = emptyList(),
    /** Set when a verify, severity or status change was refused or could not be saved. */
    val actionError: String? = null,
    /** H2: this responder is assigned and the incident is DISPATCHED, so "Start response" is offered. */
    val canShareLocation: Boolean = false,
    /** H2: this phone is sharing its location for THIS incident right now. */
    val isSharingLocation: Boolean = false,
    /** H3: the responder's own position and the road route to the incident, while sharing. */
    val responderPosition: LatLon? = null,
    val liveRoute: LiveRoute? = null,
    /** H5b: this phone is sharing for THIS incident but its GPS has been switched off. */
    val gpsLost: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Follows one incident live. The report, its merged duplicates, its assignments and the report it
 * belongs to (when it is a duplicate) all come from Firestore listeners, so a change from another
 * responder shows up without refreshing. The photo is read once when the report says it has one,
 * and again on [retryPhoto].
 *
 * The actions (E3) write to Firestore and the listeners then show the result, so a change appears
 * on screen straight away, even offline (Firestore shows its own pending write). A write the
 * server refuses is rolled back by Firestore, and [IncidentDetailUiState.actionError] says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModel(
    private val incidentId: String,
    private val incidents: IncidentRepository = IncidentRepository(),
    private val assignmentRepo: AssignmentRepository = AssignmentRepository(),
    private val auth: AuthRepository = AuthRepository()
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data object Failed : Feed
        data class Loaded(val incident: Incident?) : Feed
    }

    /** What the location service reports (H2, H3), gathered into one value so combine() stays short. */
    private data class ShareInfo(val sharingId: String?, val position: LatLon?, val route: LiveRoute?, val gpsLost: Boolean)

    private data class Extras(
        val duplicates: List<Incident>,
        val primary: Incident?,
        val photo: PhotoState,
        val assignments: List<Assignment>
    )

    /** Goes up by one on each retry, which restarts the listeners below. */
    private val restarts = MutableStateFlow(0)
    private val photoRestarts = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)

    private val feed: StateFlow<Feed> = restarts
        .flatMapLatest {
            incidents.observe(incidentId)
                .map<Incident?, Feed> { Feed.Loaded(it) }
                .onStart { emit(Feed.Loading) }
                .catch { e ->
                    Log.w(TAG, "Incident listener stopped", e)
                    emit(Feed.Failed)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Feed.Loading)

    private val current: Flow<Incident?> = feed.map { (it as? Feed.Loaded)?.incident }

    // The duplicates, assignments and photo are extras. If they fail, the report itself still shows.
    private val duplicates: Flow<List<Incident>> = restarts.flatMapLatest {
        incidents.observeDuplicatesOf(incidentId)
            .catch { e ->
                Log.w(TAG, "Duplicates listener stopped", e)
                emit(emptyList())
            }
            // Start with an empty list so the screen does not wait for this listener.
            .onStart { emit(emptyList()) }
    }

    private val assignments: Flow<List<Assignment>> = restarts.flatMapLatest {
        assignmentRepo.observe(incidentId)
            .catch { e ->
                Log.w(TAG, "Assignments listener stopped", e)
                emit(emptyList())
            }
            .onStart { emit(emptyList()) }
    }

    private val primary: Flow<Incident?> = current
        .map { it?.duplicateOf?.takeIf { parent -> parent.isNotBlank() && parent != incidentId } }
        .distinctUntilChanged()
        .flatMapLatest { parentId ->
            if (parentId == null) flowOf<Incident?>(null)
            else incidents.observe(parentId).catch { emit(null) }
        }

    private val photo: Flow<PhotoState> = combine(
        current.map { it?.hasPhoto == true }.distinctUntilChanged(),
        photoRestarts
    ) { hasPhoto, _ -> hasPhoto }
        .flatMapLatest { hasPhoto ->
            if (!hasPhoto) flowOf<PhotoState>(PhotoState.None)
            else flow<PhotoState> {
                emit(PhotoState.Loading)
                emit(loadPhoto())
            }
        }

    private val extras: Flow<Extras> = combine(duplicates, primary, photo, assignments) { merged, parent, picture, assigned ->
        Extras(merged, parent, picture, assigned)
    }

    private val share: Flow<ShareInfo> = combine(
        LocationShareState.activeIncidentId,
        LocationShareState.position,
        LocationShareState.route,
        LocationShareState.gpsLost
    ) { sharingId, position, route, gpsLost -> ShareInfo(sharingId, position, route, gpsLost) }

    val state: StateFlow<IncidentDetailUiState> =
        combine(feed, extras, actionError, share) { result, extra, error, sharing ->
            when (result) {
                Feed.Loading -> IncidentDetailUiState()
                Feed.Failed -> IncidentDetailUiState(isLoading = false, error = LOAD_ERROR)
                is Feed.Loaded -> IncidentDetailUiState(
                    incident = result.incident,
                    duplicates = IncidentDetailRules.sortDuplicates(extra.duplicates, incidentId),
                    primary = extra.primary,
                    photo = extra.photo,
                    assignments = extra.assignments,
                    actionError = error,
                    canShareLocation = LocationShareRules.canStart(
                        result.incident?.statusEnum() ?: IncidentStatus.REPORTED,
                        extra.assignments,
                        auth.currentUid
                    ),
                    isSharingLocation = sharing.sharingId == incidentId,
                    responderPosition = sharing.position.takeIf { sharing.sharingId == incidentId },
                    liveRoute = sharing.route?.takeIf { sharing.sharingId == incidentId && it.incidentId == incidentId },
                    gpsLost = sharing.gpsLost && sharing.sharingId == incidentId,
                    isLoading = false
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentDetailUiState())

    fun retry() {
        restarts.update { it + 1 }
    }

    fun retryPhoto() {
        photoRestarts.update { it + 1 }
    }

    // ---------- Actions (E3) ----------
    // Each one checks the LIVE report first, so a stale tap (another responder already acted on it)
    // does nothing instead of overwriting their change.

    /** Verify, mark false, or mark duplicate. Only while the report is still REPORTED. */
    fun review(verification: Verification) {
        val incident = state.value.incident ?: return
        if (verification == Verification.PENDING) return
        if (!IncidentActionRules.canReview(incident.statusEnum())) return
        write { incidents.setVerification(incidentId, verification) }
    }

    fun setSeverity(severity: Severity) {
        val incident = state.value.incident ?: return
        if (!IncidentActionRules.canOverrideSeverity(incident.statusEnum())) return
        if (severity == incident.severityEnum()) return // nothing to change
        write { incidents.overrideSeverity(incidentId, severity) }
    }

    fun advanceStatus() {
        val status = state.value.incident?.statusEnum() ?: return
        val next = IncidentActionRules.nextStatus(status) ?: return
        write { incidents.updateStatus(incidentId, next) }
    }

    fun stepBackStatus() {
        val status = state.value.incident?.statusEnum() ?: return
        val previous = IncidentActionRules.previousStatus(status) ?: return
        write { incidents.updateStatus(incidentId, previous) }
    }

    fun dismissActionError() {
        actionError.value = null
    }

    /**
     * Starts a write and does NOT wait for it: a Firestore write only finishes when the server
     * confirms, which would freeze the buttons offline. The listeners show the change at once.
     * If the server refuses it, Firestore undoes it locally and we show the error.
     */
    private fun write(task: () -> Task<Void>) {
        actionError.value = null
        task().addOnFailureListener { e ->
            Log.w(TAG, "Action on $incidentId failed", e)
            actionError.value = IncidentActionRules.ACTION_ERROR
        }
    }

    private suspend fun loadPhoto(): PhotoState {
        val stored = attempt { incidents.getPhoto(incidentId) }
            .getOrElse { e ->
                Log.w(TAG, "Could not read the photo of $incidentId", e)
                return PhotoState.Unavailable
            }
        // No document yet: the report arrived before its photo. "Try again" fetches it later.
        val base64 = stored?.base64?.takeIf { it.isNotBlank() } ?: return PhotoState.Unavailable
        val bitmap = withContext(Dispatchers.Default) { PhotoProcessor.decodePreview(base64) }
        return if (bitmap != null) PhotoState.Loaded(bitmap) else PhotoState.Unavailable
    }

    companion object {
        private const val TAG = "IncidentDetailVM"
        const val LOAD_ERROR = "We couldn't load this incident. Check your connection and try again."

        fun factory(incidentId: String) = viewModelFactory {
            initializer { IncidentDetailViewModel(incidentId) }
        }
    }
}
