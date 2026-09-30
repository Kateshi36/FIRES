package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.Role
import com.example.fires.data.model.User
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

/** Everything the sign-up screen shows. */
data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val contactNo: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val errors: Validators.SignUpErrors = Validators.SignUpErrors(),
    val isLoading: Boolean = false,
    val generalError: String? = null,
    /**
     * True once the Firebase account exists. If the citizen document is not saved yet, the screen
     * should lock the fields and label the button "Try again". Pressing it calls onSignUpClick()
     * again, which then only retries the document write (see below).
     */
    val accountCreated: Boolean = false
)

class SignUpViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _destinations = Channel<SessionState>(Channel.BUFFERED)
    val destinations: Flow<SessionState> = _destinations.receiveAsFlow()

    // Fields are frozen once the account exists, so the retry saves exactly what was submitted.
    private fun edit(change: (SignUpUiState) -> SignUpUiState) =
        _state.update { if (it.accountCreated) it else change(it) }

    fun onNameChange(value: String) = edit {
        it.copy(name = value, errors = it.errors.copy(name = null), generalError = null)
    }

    fun onEmailChange(value: String) = edit {
        it.copy(email = value, errors = it.errors.copy(email = null), generalError = null)
    }

    fun onContactChange(value: String) = edit {
        it.copy(contactNo = value, errors = it.errors.copy(contactNo = null), generalError = null)
    }

    fun onPasswordChange(value: String) = edit {
        it.copy(password = value, errors = it.errors.copy(password = null), generalError = null)
    }

    fun onConfirmPasswordChange(value: String) = edit {
        it.copy(
            confirmPassword = value,
            errors = it.errors.copy(confirmPassword = null),
            generalError = null
        )
    }

    /**
     * Validate -> create the account -> write users/{uid} with role = citizen.
     *
     * Half-finished case: creating the account and writing the document are two separate
     * network calls, so the first can work while the second fails. When that happens the
     * account is kept (not deleted) and accountCreated stays true. Pressing the button again
     * then skips validation and account creation and only retries the document write.
     * Nothing is lost, and the person never hits "email already in use" on their own account.
     * If they leave instead, the next app start finds no complete profile and sends them to
     * profile setup (see toSessionState), which must create the document if it is missing.
     */
    fun onSignUpClick() {
        val s = _state.value
        if (s.isLoading) return // ignore double taps

        if (s.accountCreated) {
            retryDocumentWrite()
            return
        }

        val errors = Validators.validateSignUp(
            s.name, s.email, s.contactNo, s.password, s.confirmPassword
        )
        if (!errors.isValid) {
            _state.update { it.copy(errors = errors, generalError = null) }
            return
        }

        _state.update {
            it.copy(isLoading = true, errors = Validators.SignUpErrors(), generalError = null)
        }

        viewModelScope.launch {
            // 1. Create the Firebase account (this also signs the new user in).
            val uid = attempt { auth.createAccount(s.email.trim().lowercase(), s.password) }
                .getOrElse { e ->
                    Log.w(TAG, "Account creation failed", e)
                    val failure = AuthErrors.forSignUp(e)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errors = it.errors.copy(
                                email = failure.emailError,
                                password = failure.passwordError
                            ),
                            generalError = failure.message
                        )
                    }
                    return@launch
                }

            // The account exists from here on. Remember it BEFORE the second call can fail.
            _state.update { it.copy(accountCreated = true) }

            // 2. Write users/{uid}.
            saveCitizenDocument(uid)
        }
    }

    private fun retryDocumentWrite() {
        val uid = auth.currentUid
        if (uid == null) {
            // Signed out in the meantime. The account exists, so the person should log in.
            _state.update {
                it.copy(accountCreated = false, generalError = AuthErrors.MSG_SIGNED_OUT)
            }
            return
        }
        _state.update { it.copy(isLoading = true, generalError = null) }
        viewModelScope.launch { saveCitizenDocument(uid) }
    }

    private suspend fun saveCitizenDocument(uid: String) {
        val s = _state.value
        val user = User(
            fullName = s.name.trim(),
            email = s.email.trim().lowercase(),
            // Same rule as Validators.contactNumber: spaces and dashes are not stored.
            contactNo = s.contactNo.filterNot { it == ' ' || it == '-' },
            // Always citizen. Responder and admin are only ever set in the Console / website.
            role = Role.CITIZEN.value
            // address, purok and emergencyContact are filled in on the profile setup screen.
        )

        // Firestore only finishes a write when the server confirms it, so an offline write would
        // wait forever. The timeout turns that into an error. The write stays queued on the
        // phone anyway, and a retry writes the same document again, which is harmless.
        val result = attempt { withTimeout(WRITE_TIMEOUT_MS) { users.createUser(uid, user) } }

        if (result.isSuccess) {
            // Address is still blank, so a new citizen always continues to profile setup.
            _destinations.send(SessionState.NeedsProfile)
            _state.update { it.copy(isLoading = false) }
        } else {
            Log.w(TAG, "Could not write users/$uid", result.exceptionOrNull())
            _state.update {
                it.copy(isLoading = false, generalError = AuthErrors.MSG_PROFILE_SAVE)
            }
        }
    }

    private companion object {
        const val TAG = "SignUpViewModel"
        const val WRITE_TIMEOUT_MS = 15_000L
    }
}
