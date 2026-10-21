package com.example.fires.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fires.data.model.Incident
import com.example.fires.data.repository.AssignedNotice
import com.example.fires.data.repository.AssignmentRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.NoticeRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.DashboardTab
import com.example.fires.util.DashboardTiles
import com.example.fires.util.ResponderDashboardRules
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import com.example.fires.ui.common.LatLon
import com.example.fires.util.DashboardRules
import com.example.fires.util.DistanceFilter
import com.example.fires.util.StatusFilter
import com.example.fires.util.GpsFix
import com.example.fires.util.IncidentGroup
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

/** What the responder dashboard shows (round 2 design). */
data class DashboardUiState(
    /** Every open report, grouped, most urgent first. */
    val groups: List<IncidentGroup> = emptyList(),
    /** The reports assigned to this responder (a subset of [groups]). */
    val mine: List<IncidentGroup> = emptyList(),
    val tiles: DashboardTiles = DashboardTiles(0, 0, 0),
    /** Ids of the open reports this responder is assigned to (primary or duplicate). */
    val mineIds: Set<String> = emptySet(),
    val tab: DashboardTab = DashboardTab.MINE,
    /** First name for "Hi, …". */
    val firstName: String = "",
    /** The black "You were assigned" banner. Null when there is nothing fresh to show. */
    val banner: AssignedNotice? = null,
    val bannerGroup: IncidentGroup? = null,
    /** Notices for the bell list, newest first. */
    val notices: List<AssignedNotice> = emptyList(),
    val hasUnread: Boolean = false,
    /** The incident whose card is open on the map. Null when no marker is selected. */
    val selected: IncidentGroup? = null,
    /** The responder's own position. Null until a fix arrives, or when location is off. */
    val origin: LatLon? = null,
    val recenterKey: Int = 0,
    val isLocating: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Open reports before choosing a segment. 0 means nothing is reported at all. */
    val total: Int get() = groups.size

    /** What the list segment shows. */
    val shown: List<IncidentGroup> get() = if (tab == DashboardTab.MINE) mine else groups
}

/**
 * The responder dashboard. Incidents arrive through a Firestore listener, so a new report or a status
 * change from anyone shows up on the map and in the list without refreshing. Grouping and ordering
 * are in [DashboardRules] and the "mine" / counts / banner rules in [ResponderDashboardRules]
 * (both unit tested); this class only wires them to the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    incidents: IncidentRepository = IncidentRepository(),
    private val assignments: AssignmentRepository = AssignmentRepository(),
    private val noticeRepo: NoticeRepository = NoticeRepository(),
    private val users: UserRepository = UserRepository()
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data object Failed : Feed
        data class Loaded(val list: List<Incident>) : Feed
    }

    private data class Controls(
        val tab: DashboardTab = DashboardTab.MINE,
        val selectedId: String? = null,
        val origin: LatLon? = null,
        val recenterKey: Int = 0,
        val isLocating: Boolean = false,
        val dismissedBannerId: String? = null,
        val nowMillis: Long = System.currentTimeMillis()
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Feed.Loading)

    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val noticeList = MutableStateFlow<List<AssignedNotice>>(emptyList())
    private val mineIds = MutableStateFlow<Set<String>>(emptySet())
    private val firstName = MutableStateFlow("")
    private val seenMillis = MutableStateFlow(0L)
    private val recheck = MutableStateFlow(0)
    private val checked = HashMap<String, Boolean>()
    private var lastForceKey: Any? = null

    init {
        if (uid != null) {
            viewModelScope.launch {
                noticeRepo.observeRecent(uid).catch { Log.w(TAG, "Notice listener stopped", it) }
                    .collect { noticeList.value = it }
            }
            viewModelScope.launch {
                firstName.value = try {
                    users.getUser(uid)?.fullName?.let { ResponderDashboardRules.greetingName(it) }.orEmpty()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read the responder's name", e)
                    ""
                }
            }
            // Which open reports am I assigned to? Re-read whenever the open reports change, a new
            // notice arrives (an assignment or a removal) or the screen comes back to the front.
            viewModelScope.launch {
                combine(feed, noticeList, recheck) { f, n, r -> Triple(f, n, r) }.collectLatest { (f, n, r) ->
                    if (f !is Feed.Loaded) return@collectLatest
                    val key = n.map { it.id } to r
                    val force = key != lastForceKey
                    lastForceKey = key
                    val ids = f.list.map { it.id }
                    if (force) checked.clear()
                    checked.keys.retainAll(ids.toSet())
                    val unknown = ids.filter { it !in checked }
                    if (unknown.isNotEmpty()) {
                        val answers = try {
                            coroutineScope {
                                unknown.map { id -> async { id to assignments.isAssigned(id, uid) } }.awaitAll()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Log.w(TAG, "Could not read assignments", e)
                            emptyList()
                        }
                        answers.forEach { (id, yes) -> checked[id] = yes }
                    }
                    mineIds.value = checked.filterValues { it }.keys.toSet()
                }
            }
        }
    }

    private val feedAndMine = combine(feed, mineIds, noticeList) { f, m, n -> Triple(f, m, n) }

    val state: StateFlow<DashboardUiState> = combine(feedAndMine, controls, firstName, seenMillis) { fm, c, name, seen ->
        val (current, mine, notices) = fm
        val base = DashboardUiState(
            tab = c.tab,
            origin = c.origin,
            recenterKey = c.recenterKey,
            isLocating = c.isLocating,
            firstName = name,
            notices = notices,
            hasUnread = ResponderDashboardRules.hasUnread(notices, seen)
        )
        when (current) {
            Feed.Loading -> base
            Feed.Failed -> base.copy(isLoading = false, error = LOAD_ERROR)
            is Feed.Loaded -> {
                val view = DashboardRules.view(
                    incidents = current.list,
                    status = StatusFilter.ACTIVE,
                    distance = DistanceFilter.ANY,
                    originLatitude = null,
                    originLongitude = null
                )
                val banner = ResponderDashboardRules.bannerNotice(notices, view.groups, mine, c.nowMillis, c.dismissedBannerId)
                base.copy(
                    groups = view.groups,
                    mineIds = mine,
                    mine = ResponderDashboardRules.mine(view.groups, mine),
                    tiles = ResponderDashboardRules.tiles(view.groups, mine),
                    banner = banner,
                    bannerGroup = banner?.let { b -> view.groups.firstOrNull { it.id == b.incidentId } },
                    // Looked up in the live list, so a card closes by itself if its incident is
                    // resolved while it is open.
                    selected = view.groups.firstOrNull { it.id == c.selectedId },
                    isLoading = false
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setTab(tab: DashboardTab) = controls.update { it.copy(tab = tab) }

    fun select(incidentId: String) = controls.update { it.copy(selectedId = incidentId) }

    fun clearSelection() = controls.update { it.copy(selectedId = null) }

    /** Called when the screen comes back to the front: re-reads assignments and the clock. */
    fun onResume() {
        recheck.update { it + 1 }
        controls.update { it.copy(nowMillis = System.currentTimeMillis()) }
    }

    /** The banner's close button, or opening the report it points at. */
    fun dismissBanner(noticeId: String) = controls.update { it.copy(dismissedBannerId = noticeId) }

    /** The bell list was opened: everything up to the newest notice counts as read. */
    fun markNoticesSeen(context: Context) {
        val newest = ResponderDashboardRules.newestMillis(noticeList.value)
        if (newest > seenMillis.value) {
            seenMillis.value = newest
            com.example.fires.util.NotificationPrefs.markNoticesSeen(context.applicationContext, newest)
        }
    }

    /** Loads the saved "last seen" time once, so the red dot survives restarts. */
    fun loadSeen(context: Context) {
        seenMillis.value = com.example.fires.util.NotificationPrefs.noticesSeenMs(context.applicationContext)
    }

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
