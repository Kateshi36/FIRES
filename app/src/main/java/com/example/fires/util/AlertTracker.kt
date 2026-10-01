package com.example.fires.util

import com.example.fires.data.model.Incident

/**
 * Remembers the previous incident list and turns each new snapshot into alert events (F1.6).
 * Pure Kotlin, so the "when does the baseline get set" rules are tested on the JVM.
 *
 * Not thread-safe: feed it from one coroutine (the service does).
 */
class AlertTracker {

    private var previous: List<Incident>? = null

    /**
     * @param fromCache true when Firestore built this snapshot only from the copy saved on the
     *   phone (the app just started, or the connection dropped). That copy can be old, so such a
     *   snapshot never alerts and never becomes the baseline. The first snapshot from the SERVER
     *   sets the baseline, and later server snapshots are compared to the last server snapshot.
     *   So a fire that appeared while the phone was offline alerts when the connection comes
     *   back (subject to [AlertRules.MAX_AGE_MS]).
     * @param pendingIds incidents with a change made on THIS phone that the server has not
     *   confirmed yet (F1.12). That is the responder's own tap, so it gets no status-change alert.
     *   Alerts for other incidents, and for new reports, are not affected.
     */
    fun onSnapshot(
        incidents: List<Incident>,
        fromCache: Boolean,
        pendingIds: Set<String> = emptySet(),
        nowMillis: Long
    ): List<AlertEvent> {
        if (fromCache) {
            absorbOwnChanges(incidents, pendingIds)
            return emptyList()
        }
        val events = AlertRules.detect(previous, incidents, nowMillis)
        previous = incidents
        return events.filterNot { it.kind == AlertKind.STATUS_CHANGED && it.incidentId in pendingIds }
    }

    /**
     * An own change made while the snapshot is "from cache" (for example offline) must not alert
     * when it syncs later. So the baseline takes over those incidents only, and keeps everything
     * else from the last server snapshot (the rest of a cache snapshot may be stale).
     */
    private fun absorbOwnChanges(incidents: List<Incident>, pendingIds: Set<String>) {
        val base = previous ?: return
        if (pendingIds.isEmpty()) return
        val own = incidents.filter { it.id in pendingIds }.associateBy { it.id }
        previous = base.map { own[it.id] ?: it }
    }

    /** Forget the baseline. The next server snapshot becomes the new baseline and alerts nothing. */
    fun reset() {
        previous = null
    }
}
