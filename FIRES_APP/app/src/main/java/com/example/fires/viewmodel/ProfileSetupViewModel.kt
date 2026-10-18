package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.AuthErrors
import com.example.fires.util.Validators
import com.example.fires.util.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Everything the profile setup screen shows. */
data class ProfileSetupUiState(
    val name: String = "",
    val contactNo: String = "",
    val address: String = "",
    val purok: String = "",
    val emergencyContact: String = "",
    val errors: Validators.ProfileErrors = Validators.ProfileErrors(),
    /** True while users/{uid} is being read. The form is not shown until this is false. */
    val isLoadingProfile: Boolean = true,
    val isSaving: Boolean = false,
    /**
     * The contact number is already collected at sign-up, so it is normally hidden here. It is
     * shown only when users/{uid} is missing or has no usable number, because then nothing else
     * would ever save it.
     */
    val showContactField: Boolean = false,
    /** Reading users/{uid} failed (offline). The screen shows Retry instead of the form. */
    val loadError: String? = null,
    /** Saving failed. Shown above the button; the typed values stay. */
    val generalError: String? = null
)

/**
 * Profile setup (C9): full name, address, purok and emergency contact for a citizen whose
 * profile is not complete. Reached from splash, login and sign-up through toSessionState().
 */
class ProfileSetupViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    // One-time "go here now" signal (a Channel, so a rotation cannot replay it).
    // LoggedOut means "the session is gone": the screen sends the person to login.
    private val _destinations = Channel<SessionState>(Channel.BUFFERED)
    val destinations: Flow<SessionState> = _destinations.receiveAsFlow()

    init {
        load()
    }

    /** Reads users/{uid} to prefill the form. Also called by the Retry button. */
    fun load() {
        val uid = auth.currentUid
        if (uid == null) {
            _destinations.trySend(SessionState.LoggedOut)
            return
        }
        _state.update { it.copy(isLoadingProfile = true, loadError = null) }

        viewModelScope.launch {
            val user = attempt { withTimeout(LOAD_TIMEOUT_MS) { users.getUser(uid) } }
                .getOrElse { e ->
                    Log.w(TAG, "Could not load users/$uid", e)
                    _state.update {
                        it.copy(isLoadingProfile = false, loadError = AuthErrors.MSG_LOAD_PROFILE)
                    }
                    return@launch
                }

            // This screen is only for citizens with an incomplete profile. Anyone else (a
            // responder, or a profile completed on another device) just continues.
            val next = user.toSessionState()
            if (next != SessionState.NeedsProfile) {
                _destinations.send(next)
                return@launch
            }

            _state.update {
                it.copy(
                    name = user?.fullName.orEmpty(),
                    contactNo = user?.contactNo.orEmpty(),
                    address = user?.address.orEmpty(),
                    purok = user?.purok.orEmpty(),
                    emergencyContact = user?.emergencyContact.orEmpty(),
                    showContactField = user == null || Validators.contactNumber(user.contactNo) != null,
                    isLoadingProfile = false
                )
            }
        }
    }

    // Typing clears that field's error and the banner, so old messages don't linger.
    fun onNameChange(value: String) = _state.update {
        it.copy(name = value, errors = it.errors.copy(name = null), generalError = null)
    }

    fun onContactChange(value: String) = _state.update {
        it.copy(contactNo = value, errors = it.errors.copy(contactNo = null), generalError = null)
    }

    fun onAddressChange(value: String) = _state.update {
        it.copy(address = value, errors = it.errors.copy(address = null), generalError = null)
    }

    fun onPurokChange(value: String) = _state.update {
        it.copy(purok = value, generalError = null)
    }

    fun onEmergencyContactChange(value: String) = _state.update {
        it.copy(
            emergencyContact = value,
            errors = it.errors.copy(emergencyContact = null),
            generalError = null
        )
    }

    /** Validate -> save users/{uid} -> tell the screen to open the citizen area. */
    fun onContinueClick() {
        val s = _state.value
        if (s.isSaving || s.isLoadingProfile || s.loadError != null) return // ignore double taps

        val errors = Validators.validateProfile(
            name = s.name,
            contactNo = s.contactNo,
            address = s.address,
            emergencyContact = s.emergencyContact,
            requireContact = s.showContactField
        )
        if (!errors.isValid) {
            _state.update { it.copy(errors = errors, generalError = null) }
            return
        }

        val uid = auth.currentUid
        if (uid == null) {
            _destinations.trySend(SessionState.LoggedOut)
            return
        }

        _state.update { it.copy(isSaving = true, errors = Validators.ProfileErrors(), generalError = null) }

        viewModelScope.launch {
            // Firestore only finishes a write when the server confirms it, so an offline write
            // would wait forever. The timeout turns that into an error. The write stays queued on
            // the phone, and a retry writes the same values again, which is harmless.
            val result = attempt {
                withTimeout(WRITE_TIMEOUT_MS) {
                    users.saveProfile(
                        uid = uid,
                        email = auth.currentEmail.orEmpty(),
                        fullName = s.name.trim(),
                        contactNo = Validators.normalizePhone(s.contactNo),
                        address = s.address.trim(),
                        purok = s.purok.trim(),
                        emergencyContact = Validators.normalizePhone(s.emergencyContact)
                    )
                }
            }

            if (result.isSuccess) {
                _destinations.send(SessionState.Citizen)
                _state.update { it.copy(isSaving = false) }
            } else {
                val e = result.exceptionOrNull()
                Log.w(TAG, "Could not save profile for users/$uid", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        generalError = AuthErrors.forProfileSave(e ?: IllegalStateException())
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "ProfileSetupViewModel"
        const val LOAD_TIMEOUT_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 15_000L
    }
}
