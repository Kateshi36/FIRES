package com.example.fires.service

import android.content.Context
import android.util.Log
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.MessageRepository
import com.example.fires.util.CitizenAlertRules
import com.example.fires.util.CitizenStatusTracker
import com.example.fires.util.CitizenViewing
import com.example.fires.util.CitizenWatchRules
import com.example.fires.util.ReplyTracker
import com.example.fires.util.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Local notifications for a citizen while the app is running (F2): one of my reports changed
 * status, or a responder replied in its chat. Nothing is sent from a server and there is no
 * foreground service: this listens from inside the app process, so it works for as long as
 * Android keeps the process alive, which includes a while after the app is sent to the
 * background. A closed app gets no notifications (that would need Firebase Cloud Messaging).
 *
 * Started by AppNavHost when the citizen area is showing, and stopped on sign-out.
 */
object CitizenAlertWatcher {

    private const val TAG = "CitizenAlertWatcher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var watchedUid: String? = null

    /** Safe to call again and again: it does nothing while the watcher is already running for this account. */
    @Synchronized
    fun start(context: Context) {
        val uid = AuthRepository().currentUid ?: return
        if (job?.isActive == true && watchedUid == uid) return
        job?.cancel()
        watchedUid = uid
        val appContext = context.applicationContext
        job = scope.launch { watch(appContext, uid) }
    }

    /** Ends every listener. Call BEFORE signing out, so nothing for this account arrives after it. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        watchedUid = null
    }

    private suspend fun watch(context: Context, uid: String) {
        val notifier = CitizenNotifier(context)
        val statusTracker = CitizenStatusTracker()
        val threads = mutableMapOf<String, Job>()

        // The incident listener only ends by failing (for example permission denied once the
        // session is gone) or by cancellation. attempt() rethrows cancellation.
        val result = attempt {
            coroutineScope {
                IncidentRepository().observeMineSnapshots(uid).collect { snapshot ->
                    // 1. Status changes of my reports.
                    statusTracker.onSnapshot(snapshot.items, snapshot.fromCache, System.currentTimeMillis())
                        .filterNot { CitizenAlertRules.isSuppressed(it, CitizenViewing.current) }
                        .forEach(notifier::post)

                    // 2. One chat listener per open report. Closed reports drop theirs.
                    val wanted = CitizenWatchRules.threadsToWatch(snapshot.items)
                    threads.keys.filter { it !in wanted }.toList().forEach { threads.remove(it)?.cancel() }
                    wanted.filter { threads[it]?.isActive != true }.forEach { id ->
                        threads[id] = launch { watchReplies(notifier, id, uid) }
                    }
                }
            }
        }
        Log.w(TAG, "Report listener ended", result.exceptionOrNull())
    }

    private suspend fun watchReplies(notifier: CitizenNotifier, incidentId: String, uid: String) {
        val tracker = ReplyTracker(incidentId)
        // A failing chat listener must not take the whole watcher down with it (a failing child
        // would cancel its parent), so its failure is only logged. It is started again the next
        // time the report list changes.
        val result = attempt {
            MessageRepository().observeSnapshots(incidentId).collect { snapshot ->
                val alert = tracker.onSnapshot(
                    messages = snapshot.items,
                    fromCache = snapshot.fromCache,
                    myUid = uid,
                    nowMillis = System.currentTimeMillis()
                ) ?: return@collect
                if (!CitizenAlertRules.isSuppressed(alert, CitizenViewing.current)) notifier.post(alert)
            }
        }
        Log.w(TAG, "Chat listener for $incidentId ended", result.exceptionOrNull())
    }
}
