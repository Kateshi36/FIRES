package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.User
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.Validators
import com.example.fires.util.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the edit dialog holds while it is open. */
data class ProfileForm(
    val name: String = "",
    val contactNo: String = "",
    val address: String = "",
    val purok: String = "",
    val emergencyContact: String = ""
)

/** What the citizen Profile screen shows. */
data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Null while the edit dialog is closed. */
    val form: ProfileForm? = null,
    val errors: Validators.ProfileErrors = Validators.ProfileErrors(),
    val isSaving: Boolean = false,
    val saveError: String? = null
)

/**
 * The signed-in citizen's own details, kept live, plus the edit dialog. Only profile fields can be
 * changed here: the rules refuse a change of role, and updateProfile() never sends one.
 */
class ProfileViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var watchJob: Job? = null

    init { watch() }

    private fun watch() {
        val uid = auth.currentUid
        if (uid == null) {
            _state.update { it.copy(isLoading = false, error = SIGNED_OUT) }
            return
        }
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            val result = attempt {
                users.observeUser(uid).collect { user ->
                    _state.update { it.copy(user = user, isLoading = false, error = null) }
                }
            }
            result.exceptionOrNull()?.let { e ->
                Log.w(TAG, "Profile listener stopped", e)
                _state.update { it.copy(isLoading = false, error = LOAD_ERROR) }
            }
        }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        watch()
    }

    fun openEdit() {
        val u = _state.value.user ?: return
        _state.update {
            it.copy(
                form = ProfileForm(u.fullName, u.contactNo, u.address, u.purok, u.emergencyContact),
                errors = Validators.ProfileErrors(), saveError = null
            )
        }
    }

    fun closeEdit() = _state.update { it.copy(form = null, errors = Validators.ProfileErrors(), saveError = null) }

    fun onForm(change: (ProfileForm) -> ProfileForm) =
        _state.update { s -> s.form?.let { s.copy(form = change(it), saveError = null) } ?: s }

    fun save() {
        val s = _state.value
        val form = s.form ?: return
        val uid = auth.currentUid ?: return
        if (s.isSaving) return
        val errors = Validators.validateProfile(
            name = form.name, contactNo = form.contactNo, address = form.address,
            emergencyContact = form.emergencyContact, requireContact = true
        )
        if (!errors.isValid) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(isSaving = true, errors = Validators.ProfileErrors(), saveError = null) }
        viewModelScope.launch {
            // Offline, a Firestore write waits for the connection forever; the timeout turns that into a message.
            val done = attempt {
                withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                    users.updateProfile(
                        uid = uid,
                        fullName = form.name.trim(),
                        contactNo = Validators.normalizePhone(form.contactNo),
                        address = form.address.trim(),
                        purok = form.purok.trim(),
                        emergencyContact = Validators.normalizePhone(form.emergencyContact)
                    )
                    true
                }
            }
            if (done.getOrNull() == true) {
                _state.update { it.copy(isSaving = false, form = null) }
            } else {
                done.exceptionOrNull()?.let { Log.w(TAG, "Profile save failed", it) }
                _state.update { it.copy(isSaving = false, saveError = SAVE_ERROR) }
            }
        }
    }

    companion object {
        private const val TAG = "ProfileViewModel"
        private const val SAVE_TIMEOUT_MS = 10_000L
        const val LOAD_ERROR = "We couldn't load your profile. Check your connection and try again."
        const val SIGNED_OUT = "Your session has ended. Please log in again."
        const val SAVE_ERROR = "We couldn't save your changes. Check your connection and try again."
    }
}
