package com.example.fires.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.Incident
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.ui.common.LatLon
import com.example.fires.util.DashboardRules
import com.example.fires.util.DistanceFilter
import com.example.fires.util.GpsFix
import com.example.fires.util.IncidentGroup
import com.example.fires.util.StatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DashboardTab { MAP, LIST }

/** What the responder dashboard shows (E1). */
data class DashboardUiState(
    /** Active incidents after grouping and both filters, most urgent first. */
    val groups: List<IncidentGroup> = emptyList(),
    /** Number on each status chip. */
    val counts: Map<StatusFilter, Int> = emptyMap(),
    /** Active incidents before any filter. 0 means nothing is reported at all. */
    val total: Int = 0,
    val statusFilter: StatusFilter = StatusFilter.ACTIVE,
    val distanceFilter: DistanceFilter = DistanceFilter.ANY,
    val tab: DashboardTab = DashboardTab.MAP,
    /** The incident whose card is open on the map. Null when no marker is selected. */
    val selected: IncidentGroup? = null,
    /** The responder's own position. Null until a fix arrives, or when location is off. */
    val origin: LatLon? = null,
    val recenterKey: Int = 0,
    val isLocating: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isFiltered: Boolean get() = statusFilter != StatusFilter.ACTIVE || distanceFilter != DistanceFilter.ANY
}

/**
 * The responder dashboard. Incidents arrive through a Firestore listener, so a new report or a status
 * change from anyone shows up on the map and in the list without refreshing. Grouping, filtering and
 * ordering are in [DashboardRules] (unit tested); this class only wires them to the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data object Failed : Feed
        data class Loaded(val list: List<Incident>) : Feed
    }

    private data class Controls(
        val status: StatusFilter = StatusFilter.ACTIVE,
        val distance: DistanceFilter = DistanceFilter.ANY,
        val tab: DashboardTab = DashboardTab.MAP,
        val selectedId: String? = null,
        val origin: LatLon? = null,
        val recenterKey: Int = 0,
        val isLocating: Boolean = false
    )

    private val restarts = MutableStateFlow(0)
    private val controls = MutableStateFlow(Controls())
    private var locateJob: Job? = null

    private val feed = restarts.flatMapLatest {
        incidents.observeActive()
            .map<List<Incident>, Feed> { Feed.Loaded(it) }
            .onStart { emit(Feed.Loading) }
            .catch { e ->
                Log.w(TAG, "Dashboard listener stopped", e)
                emit(Feed.Failed)
            }
    }

    val state: StateFlow<DashboardUiState> = combine(feed, controls) { current, c ->
        val base = DashboardUiState(
            statusFilter = c.status,
            distanceFilter = c.distance,
            tab = c.tab,
            origin = c.origin,
            recenterKey = c.recenterKey,
            isLocating = c.isLocating
        )
        when (current) {
            Feed.Loading -> base
            Feed.Failed -> base.copy(isLoading = false, error = LOAD_ERROR)
            is Feed.Loaded -> {
                val view = DashboardRules.view(
                    incidents = current.list,
                    status = c.status,
                    distance = c.distance,
                    originLatitude = c.origin?.latitude,
                    originLongitude = c.origin?.longitude
                )
                base.copy(
                    groups = view.groups,
                    counts = view.counts,
                    total = view.total,
                    // Looked up in the live list, so a card closes by itself if its incident is
                    // resolved or filtered out while it is open.
                    selected = view.groups.firstOrNull { it.id == c.selectedId },
                    isLoading = false
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setTab(tab: DashboardTab) = controls.update { it.copy(tab = tab) }

    fun setStatusFilter(filter: StatusFilter) = controls.update { it.copy(status = filter) }

    /** A distance limit needs the responder's position, so it is ignored until one is known. */
    fun setDistanceFilter(filter: DistanceFilter) = controls.update {
        if (filter != DistanceFilter.ANY && it.origin == null) it else it.copy(distance = filter)
    }

    fun select(incidentId: String) = controls.update { it.copy(selectedId = incidentId) }

    fun clearSelection() = controls.update { it.copy(selectedId = null) }

    fun retry() {
        restarts.update { it + 1 }
    }

    /** Reads the GPS position. Safe to call often; a second call while one is running is ignored. */
    fun refreshLocation(context: Context) {
        if (locateJob?.isActive == true) return
        val appContext = context.applicationContext
        locateJob = viewModelScope.launch {
            controls.update { it.copy(isLocating = true) }
            val fix = GpsFix.current(appContext)
            controls.update {
                it.copy(
                    origin = fix ?: it.origin,
                    isLocating = false,
                    recenterKey = if (fix != null) it.recenterKey + 1 else it.recenterKey
                )
            }
        }
    }

    companion object {
        private const val TAG = "DashboardViewModel"
        const val LOAD_ERROR = "We couldn't load the incidents. Check your connection and try again."
    }
}
