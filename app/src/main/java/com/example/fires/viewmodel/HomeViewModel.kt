package com.example.fires.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.ui.common.LatLon
import com.example.fires.util.GpsFix
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the citizen Home screen shows. */
data class HomeUiState(
    /** The phone's position, or null until a fix arrives (or when location is off). */
    val position: LatLon? = null,
    val isLocating: Boolean = false,
    /** Goes up by one each time a new fix arrives, so the map glides back to it even if the spot is the same. */
    val recenterKey: Int = 0
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var locateJob: Job? = null

    /** Reads the GPS position. Safe to call often; a second call while one is running is ignored. */
    fun refreshLocation(context: Context) {
        if (locateJob?.isActive == true) return
        val appContext = context.applicationContext
        locateJob = viewModelScope.launch {
            _state.update { it.copy(isLocating = true) }
            val fix = GpsFix.current(appContext)
            _state.update {
                it.copy(
                    position = fix ?: it.position,
                    isLocating = false,
                    recenterKey = if (fix != null) it.recenterKey + 1 else it.recenterKey
                )
            }
        }
    }
}
