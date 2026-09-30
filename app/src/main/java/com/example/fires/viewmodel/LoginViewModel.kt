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

/** Everything the login screen shows. The screen only reads this and calls the on... functions. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val errors: Validators.LoginErrors = Validators.LoginErrors(), // messages under the fields
    val isLoading: Boolean = false,
    val generalError: String? = null                                // banner above the button
)

class LoginViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    // One-time "go here now" signal. A Channel (not state) so a screen rotation
    // cannot replay it and navigate twice.
    private val _destinations = Channel<SessionState>(Channel.BUFFERED)
    val destinations: Flow<SessionState> = _destinations.receiveAsFlow()

    // Typing clears that field's error and the banner, so old messages don't linger.
    fun onEmailChange(value: String) = _state.update {
        it.copy(email = value, errors = it.errors.copy(email = null), generalError = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, errors = it.errors.copy(password = null), generalError = null)
    }

    /** Validate -> sign in -> read users/{uid} -> tell the screen where to go. */
    fun onLoginClick() {
        val s = _state.value
        if (s.isLoading) return // ignore double taps

        val errors = Validators.validateLogin(s.email, s.password)
        if (!errors.isValid) {
            _state.update { it.copy(errors = errors, generalError = null) }
            return
        }

        _state.update { it.copy(isLoading = true, errors = Validators.LoginErrors(), generalError = null) }

        viewModelScope.launch {
            // 1. Sign in with Firebase Auth.
            val uid = attempt { auth.signIn(s.email.trim().lowercase(), s.password) }
                .getOrElse { e ->
                    Log.w(TAG, "Sign-in failed", e)
                    fail(AuthErrors.forLogin(e))
                    return@launch
                }

            // 2. Read users/{uid} to get the role. If this fails we sign out again, so
            //    "login failed" always means "not logged in" and no session is left half-open.
            val user = attempt { users.getUser(uid) }
                .getOrElse { e ->
                    Log.w(TAG, "Could not read users/$uid", e)
                    auth.signOut()
                    fail(AuthErrors.MSG_LOAD_PROFILE)
                    return@launch
                }

            // 3. Same routing rule as the splash screen: role decides the area,
            //    a missing or incomplete citizen profile goes to profile setup.
            _destinations.send(user.toSessionState())
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun fail(message: String) =
        _state.update { it.copy(isLoading = false, generalError = message) }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}
