package com.example.fires.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType
import com.example.fires.data.model.HazardType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentPhoto
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.LocationSource
import com.example.fires.data.model.SeveritySource
import com.example.fires.data.model.Verification
import com.example.fires.data.model.VulnerableGroup
import com.example.fires.data.model.statusEnum
import com.example.fires.data.model.verificationEnum
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.ui.common.LatLon
import com.example.fires.util.AddressLookup
import com.example.fires.util.GpsFix
import com.example.fires.util.PhotoProcessor
import com.example.fires.util.ReportChecks
import com.example.fires.util.ReportValidators
import com.example.fires.util.SeverityCalculator
import com.example.fires.util.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Everything the report form shows. */
data class ReportUiState(
    val fireType: FireType? = null,
    val description: String = "",
    val fireSize: FireSize? = null,
    val peopleAtRisk: Int = 0,
    val vulnerable: Set<VulnerableGroup> = emptySet(),
    val trapped: Boolean = false,
    val hazards: Set<HazardType> = emptySet(),

    /** The optional photo, already shrunk and compressed (about 200 KB at most). */
    val photo: IncidentPhoto? = null,
    val isProcessingPhoto: Boolean = false,
    /** Why the last picked photo was refused. The previous photo, if any, is kept. */
    val photoError: String? = null,

    /** Where the fire is. Null until GPS or the pin gives one. */
    val location: LatLon? = null,
    /** How [location] was set: the phone's GPS or the person moving the pin. */
    val locationSource: LocationSource = LocationSource.GPS,
    /** The phone's own position, shown as the blue dot on the pin screen. */
    val gps: LatLon? = null,
    val isLocating: Boolean = false,

    val addressText: String = "",
    val isLookingUpAddress: Boolean = false,

    val errors: ReportValidators.ReportErrors = ReportValidators.ReportErrors(),
    /** True after a Send tap with missing fields; shows the "check the fields above" message. */
    val showFormError: Boolean = false,

    /** True while the report is being saved. The Send button shows a spinner. */
    val isSubmitting: Boolean = false,
    /** The report could not be sent. Shown above the Send button; the form keeps everything typed. */
    val submitError: String? = null
)

/** One-time signals from the ViewModel to the screen. */
sealed interface ReportEvent {
    /** The report was saved (or queued while offline) under [incidentId]. */
    data class Sent(val incidentId: String) : ReportEvent
}

/**
 * The report form (D2). Lives as long as the form is open, and is shared with the pin screen,
 * so the pin screen can hand its result straight back.
 */
class ReportViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository(),
    private val incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events: Flow<ReportEvent> = _events.receiveAsFlow()

    private var locateJob: Job? = null
    private var addressJob: Job? = null
    private var photoJob: Job? = null

    /** True once the person types their own address, so a late lookup does not overwrite it. */
    private var addressEdited = false

    // ---------- Simple fields ----------

    fun onFireTypeChange(type: FireType) =
        _state.update { it.copy(fireType = type, errors = it.errors.copy(fireType = null)) }

    fun onDescriptionChange(text: String) = _state.update {
        it.copy(
            description = text.take(ReportValidators.MAX_DESCRIPTION),
            errors = it.errors.copy(description = null)
        )
    }

    fun onFireSizeChange(size: FireSize) =
        _state.update { it.copy(fireSize = size, errors = it.errors.copy(fireSize = null)) }

    fun onPeopleChange(count: Int) =
        _state.update { it.copy(peopleAtRisk = count.coerceIn(0, ReportValidators.MAX_PEOPLE)) }

    fun onVulnerableToggle(group: VulnerableGroup) = _state.update {
        it.copy(vulnerable = if (group in it.vulnerable) it.vulnerable - group else it.vulnerable + group)
    }

    fun onTrappedChange(trapped: Boolean) = _state.update { it.copy(trapped = trapped) }

    fun onHazardToggle(hazard: HazardType) = _state.update {
        it.copy(hazards = if (hazard in it.hazards) it.hazards - hazard else it.hazards + hazard)
    }

    fun onAddressChange(text: String) {
        addressEdited = true
        _state.update { it.copy(addressText = text) }
    }

    // ---------- Photo ----------

    /** The person picked a photo. It is checked, rotated, shrunk and compressed off the main thread. */
    fun onPhotoPicked(context: Context, uri: Uri) {
        if (photoJob?.isActive == true) return
        val appContext = context.applicationContext
        photoJob = viewModelScope.launch {
            _state.update { it.copy(isProcessingPhoto = true, photoError = null) }
            when (val result = PhotoProcessor.process(appContext, uri)) {
                is PhotoProcessor.Result.Success ->
                    _state.update { it.copy(photo = result.photo, isProcessingPhoto = false) }
                is PhotoProcessor.Result.Failure ->
                    _state.update { it.copy(photoError = result.message, isProcessingPhoto = false) }
            }
        }
    }

    fun onPhotoRemove() = _state.update { it.copy(photo = null, photoError = null) }

    /**
     * The photo to save with the report, or null when there is none. The Submit stage saves it with
     * IncidentRepository.savePhoto(incidentId, photo) (the incident_photos collection) and sets
     * Incident.hasPhoto = true.
     */
    fun photoForSave(): IncidentPhoto? = _state.value.photo

    // ---------- Location ----------

    /**
     * Reads the GPS. When [force] is false (automatic, on opening the form) the result only
     * becomes the fire's location if none is set yet, so it never overrides a pin the person
     * placed while the fix was still loading. When [force] is true (the person tapped
     * "Use my GPS location") it always replaces the location.
     */
    fun useGps(context: Context, force: Boolean) {
        if (locateJob?.isActive == true) return
        val appContext = context.applicationContext
        locateJob = viewModelScope.launch {
            _state.update { it.copy(isLocating = true) }
            val fix = GpsFix.current(appContext)
            _state.update { it.copy(isLocating = false, gps = fix ?: it.gps) }
            if (fix != null && (force || _state.value.location == null)) {
                setLocation(appContext, fix, LocationSource.GPS)
            }
        }
    }

    /** The pin screen's Confirm button. */
    fun confirmPin(context: Context, point: LatLon) =
        setLocation(context.applicationContext, point, LocationSource.PIN)

    private fun setLocation(appContext: Context, point: LatLon, source: LocationSource) {
        addressEdited = false
        addressJob?.cancel()
        _state.update {
            it.copy(
                location = point,
                locationSource = source,
                addressText = "",
                isLookingUpAddress = true,
                errors = it.errors.copy(location = null)
            )
        }
        addressJob = viewModelScope.launch {
            val address = AddressLookup.reverse(appContext, point)
            _state.update { s ->
                if (!addressEdited && s.location == point) {
                    s.copy(addressText = address.orEmpty(), isLookingUpAddress = false)
                } else {
                    s.copy(isLookingUpAddress = false)
                }
            }
        }
    }

    // ---------- Send ----------

    // The id of this report once chosen. A retry after a failure saves to the SAME document,
    // so a second tap can never create a second incident.
    private var pendingId: String? = null
    private var sendJob: Job? = null

    /**
     * Send tap: check the required fields, run the duplicate and suspicious checks, work out the
     * severity, and save the report (and the photo, if any). On success the screen gets
     * [ReportEvent.Sent] with the incident id.
     *
     * The checks only set flags. They can never stop a report from being saved.
     */
    fun onSendClick() {
        if (sendJob?.isActive == true) return
        val s = _state.value
        val errors = ReportValidators.validate(
            fireType = s.fireType,
            description = s.description,
            fireSize = s.fireSize,
            hasLocation = s.location != null
        )
        _state.update { it.copy(errors = errors, showFormError = errors.hasErrors, submitError = null) }
        if (errors.hasErrors) return

        val fireType = s.fireType ?: return
        val fireSize = s.fireSize ?: return
        val where = s.location ?: return

        sendJob = viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, submitError = null) }

            val uid = auth.currentUid
            if (uid == null) {
                _state.update { it.copy(isSubmitting = false, submitError = MSG_SESSION_ENDED) }
                return@launch
            }

            // The id is chosen first, and kept for retries, so the checks below can ignore this
            // report's own earlier attempt.
            val id = pendingId ?: incidents.newId().also { pendingId = it }
            val now = System.currentTimeMillis()

            // Three quick reads, run side by side under one time limit each, so a weak connection
            // delays the report by PRE_SAVE_WAIT_MS at most (not by the sum of the three):
            //  - the profile: name and phone number go with the report so responders can call
            //    back. Without it the report is still sent, and responders can look the person up
            //    by reporterId.
            //  - recent reports from anyone: to spot a duplicate of a nearby fire.
            //  - this account's own reports: to spot spam or a history of false reports.
            // A read that fails just means "no flag from that check". In a fire, sending matters
            // more than checking.
            val (reporter, recent, mine) = coroutineScope {
                val profile = async { lookup("Reporter profile") { users.getUser(uid) } }
                val recentReports = async {
                    lookup("Duplicate check") {
                        incidents.recentIncidents(now - ReportChecks.DUPLICATE_WINDOW_MILLIS)
                    }
                }
                val ownReports = async { lookup("Suspicious check") { incidents.myIncidents(uid) } }
                Triple(profile.await(), recentReports.await(), ownReports.await())
            }

            val duplicateOf = recent?.let { reports ->
                ReportChecks.findDuplicate(
                    latitude = where.latitude,
                    longitude = where.longitude,
                    candidates = reports.filter { it.id != id }.map { it.toNearbyReport() },
                    nowMillis = now
                )
            }
            val suspicious = mine?.let { reports ->
                ReportChecks.isSuspicious(
                    past = reports.filter { it.id != id }.map { it.toPastReport() },
                    nowMillis = now
                )
            } ?: false

            // Severity is worked out here, once, and saved with the report as "auto".
            val severity = SeverityCalculator.calculate(
                fireType = fireType,
                fireSize = fireSize,
                peopleAtRisk = s.peopleAtRisk,
                vulnerable = s.vulnerable,
                trapped = s.trapped
            ).severity

            val incident = Incident(
                reporterId = uid,
                reporterName = reporter?.fullName.orEmpty(),
                reporterContact = reporter?.contactNo.orEmpty(),
                fireType = fireType.value,
                description = s.description.trim(),
                latitude = where.latitude,
                longitude = where.longitude,
                locationSource = s.locationSource.value,
                addressText = s.addressText.trim(),
                fireSize = fireSize.value,
                peopleAtRisk = s.peopleAtRisk,
                vulnerablePersons = s.vulnerable.map { it.value },
                trapped = s.trapped,
                hazards = s.hazards.map { it.value },
                hasPhoto = s.photo != null,
                status = IncidentStatus.REPORTED.value,
                verification = Verification.PENDING.value,
                severity = severity.value,
                severitySource = SeveritySource.AUTO.value,
                isDuplicate = duplicateOf != null,
                duplicateOf = duplicateOf,
                isSuspicious = suspicious
                // submittedAt / updatedAt are filled in by the server (@ServerTimestamp).
            )

            // Both writes are started together; the report does not depend on the photo being
            // saved first. Firestore keeps them in a local queue when there is no signal.
            val saveReport = incidents.save(id, incident)
            val savePhoto = s.photo?.let { incidents.savePhoto(id, it) }

            // Wait a short while for the server to confirm. No answer in time usually means no
            // signal: the report is safely queued on the phone and goes out when the phone is
            // back online, so the person moves on. A real refusal from the server (for example
            // a rules error) is shown as an error instead.
            val outcome = withTimeoutOrNull(SERVER_WAIT_MS) {
                attempt {
                    saveReport.await()
                    savePhoto?.await()
                }
            }
            if (outcome != null && outcome.isFailure) {
                _state.update { it.copy(isSubmitting = false, submitError = MSG_SEND_FAILED) }
                return@launch
            }

            _state.update { it.copy(isSubmitting = false) }
            _events.trySend(ReportEvent.Sent(id))
        }
    }

    /** A read done before saving: null (and a log line) if it fails or takes too long. */
    private suspend fun <T> lookup(what: String, block: suspend () -> T): T? =
        attempt { withTimeout(PRE_SAVE_WAIT_MS) { block() } }
            .onFailure { Log.w(TAG, "$what skipped", it) }
            .getOrNull()

    private companion object {
        const val TAG = "ReportViewModel"
        const val PRE_SAVE_WAIT_MS = 4_000L
        const val SERVER_WAIT_MS = 6_000L
        const val MSG_SESSION_ENDED = "Your session ended. Please log in again."
        const val MSG_SEND_FAILED = "We couldn't send your report. Check your connection and tap Send again."
    }
}

// What the checks need from a saved report. The Firestore model stays out of ReportChecks, so the
// checks can be tested without Firebase.

private fun Incident.toNearbyReport() = ReportChecks.NearbyReport(
    id = id,
    latitude = latitude,
    longitude = longitude,
    status = statusEnum(),
    duplicateOf = duplicateOf,
    submittedAtMillis = submittedAt?.toDate()?.time
)

private fun Incident.toPastReport() = ReportChecks.PastReport(
    submittedAtMillis = submittedAt?.toDate()?.time,
    verification = verificationEnum()
)
