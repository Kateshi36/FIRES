package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.Incident
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.RecordRepository
import com.example.fires.util.DateRange
import com.example.fires.util.HistoryRules
import com.example.fires.util.HistoryStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/** One row in the history list: the report, and the remarks written when it was resolved (if any). */
data class HistoryItem(val incident: Incident, val remarks: String?)

/** What the Incident history screen shows (E7). */
data class HistoryUiState(
    /** Closed reports that pass both filters, newest first. */
    val items: List<HistoryItem> = emptyList(),
    /** Number on each status chip, with the date filter applied. */
    val counts: Map<HistoryStatusFilter, Int> = emptyMap(),
    /** Closed reports before any filter. 0 means nothing has been closed yet. */
    val total: Int = 0,
    val statusFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val dateRange: DateRange = DateRange.ANY,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isFiltered: Boolean get() = statusFilter != HistoryStatusFilter.ALL || !dateRange.isAny
}

/**
 * Closed reports (resolved, false report, duplicate), live. Filtering and ordering are in
 * [HistoryRules] (unit tested); this class only wires them to the screen. The remarks come from
 * records/{incidentId} and are an extra: if they cannot be read, the list still shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    incidents: IncidentRepository = IncidentRepository(),
    records: RecordRepository = RecordRepository()
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data object Failed : Feed
        data class Loaded(val list: List<Incident>) : Feed
    }

    private data class Controls(
        val status: HistoryStatusFilter = HistoryStatusFilter.ALL,
        val range: DateRange = DateRange.ANY
    )

    private val restarts = MutableStateFlow(0)
    private val controls = MutableStateFlow(Controls())

    private val feed = restarts.flatMapLatest {
        incidents.observeClosed()
            .map<List<Incident>, Feed> { Feed.Loaded(it) }
            .onStart { emit(Feed.Loading) }
            .catch { e ->
                Log.w(TAG, "History listener stopped", e)
                emit(Feed.Failed)
            }
    }

    private val remarks: Flow<Map<String, String>> = restarts.flatMapLatest {
        records.observeAll()
            .map { list -> list.filter { it.remarks.isNotBlank() }.associate { it.incidentId to it.remarks } }
            .catch { e ->
                Log.w(TAG, "Records listener stopped", e)
                emit(emptyMap())
            }
            .onStart { emit(emptyMap()) }
    }

    val state: StateFlow<HistoryUiState> = combine(feed, remarks, controls) { current, notes, c ->
        val base = HistoryUiState(statusFilter = c.status, dateRange = c.range)
        when (current) {
            Feed.Loading -> base
            Feed.Failed -> base.copy(isLoading = false, error = LOAD_ERROR)
            is Feed.Loaded -> {
                val view = HistoryRules.view(current.list, c.status, c.range)
                base.copy(
                    items = view.incidents.map { HistoryItem(it, notes[it.id]) },
                    counts = view.counts,
                    total = view.total,
                    isLoading = false
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setStatusFilter(filter: HistoryStatusFilter) = controls.update { it.copy(status = filter) }

    fun setDateRange(range: DateRange) = controls.update { it.copy(range = range) }

    fun clearFilters() = controls.update { Controls() }

    fun retry() {
        restarts.update { it + 1 }
    }

    companion object {
        private const val TAG = "HistoryViewModel"
        const val LOAD_ERROR = "We couldn't load the history. Check your connection and try again."
    }
}
