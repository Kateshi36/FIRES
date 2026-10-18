package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.Incident
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** What the "My reports" list shows (D7). Newest first. */
data class MyReportsUiState(
    val reports: List<Incident> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * The signed-in citizen's own reports, kept live: a status change made by a responder updates the
 * row here at once. Only this account's reports are queried (reporterId == uid), which matches the
 * rule that citizens can read only their own incidents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyReportsViewModel(
    auth: AuthRepository = AuthRepository(),
    incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private val uid: String? = auth.currentUid
    private val restarts = MutableStateFlow(0)

    val state: StateFlow<MyReportsUiState> = restarts
        .flatMapLatest {
            if (uid == null) {
                flow { emit(MyReportsUiState(isLoading = false, error = SIGNED_OUT)) }
            } else {
                incidents.observeMine(uid)
                    .map { MyReportsUiState(reports = it, isLoading = false) }
                    .onStart { emit(MyReportsUiState()) }
                    .catch { e ->
                        Log.w(TAG, "My reports listener stopped", e)
                        emit(MyReportsUiState(isLoading = false, error = LOAD_ERROR))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyReportsUiState())

    fun retry() {
        restarts.value += 1
    }

    companion object {
        private const val TAG = "MyReportsViewModel"
        const val LOAD_ERROR = "We couldn't load your reports. Check your connection and try again."
        const val SIGNED_OUT = "Your session has ended. Please log in again."
    }
}
