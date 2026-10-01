package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.AuthErrors
import com.example.fires.util.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// SessionState and User?.toSessionState() live in SessionState.kt. Do not redeclare them here.

class SplashViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        viewModelScope.launch {
            _state.value = SessionState.Loading
            val uid = auth.currentUid
            if (uid == null) {
                _state.value = SessionState.LoggedOut
                return@launch
            }
            // Offline with nothing cached fails fast; a weak connection can hang, so cap the wait.
            // The raw exception text goes to the log only, never to the screen.
            _state.value = attempt { withTimeout(LOAD_TIMEOUT_MS) { users.getUser(uid) } }
                .fold(
                    onSuccess = { it.toSessionState() },
                    onFailure = { e ->
                        Log.w(TAG, "Could not load users/$uid", e)
                        SessionState.Error(AuthErrors.MSG_LOAD_PROFILE)
                    }
                )
        }
    }

    /** Escape hatch from the error state (e.g. account deleted): clear the session, go to login. */
    fun signOut() {
        auth.signOut()
        _state.value = SessionState.LoggedOut
    }

    private companion object {
        const val TAG = "SplashViewModel"
        const val LOAD_TIMEOUT_MS = 10_000L
    }
}
