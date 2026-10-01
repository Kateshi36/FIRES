package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Message
import com.example.fires.data.model.Role
import com.example.fires.data.model.statusEnum
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Citizen notifications (F2): a report I sent changed status, or a responder replied in its chat.
 * Pure Kotlin, so every rule is tested on the JVM (CitizenAlertsTest). The watcher feeds
 * snapshots in and turns the returned alerts into notifications.
 */

enum class CitizenAlertKind { STATUS, REPLY }

/** One notification to show. [incidentId] is also what the tap opens. */
data class CitizenAlert(
    val incidentId: String,
    val kind: CitizenAlertKind,
    val title: String,
    val text: String
)

/** Which citizen screen is open right now, so it does not notify about what it already shows. */
enum class ViewedScreen { NONE, STATUS, CHAT }

data class Viewing(val screen: ViewedScreen = ViewedScreen.NONE, val incidentId: String? = null)

object CitizenAlertRules {

    /** Same freshness limit as the responder alerts: a late, stale update must not alert. */
    const val MAX_AGE_MS = AlertRules.MAX_AGE_MS

    private const val MAX_REPLY_CHARS = 300

    /** No timestamp yet (a write still waiting for the server) counts as fresh. */
    fun isFresh(time: Timestamp?, nowMillis: Long): Boolean =
        time == null || nowMillis - time.seconds * 1000 <= MAX_AGE_MS

    /** Reuses the wording of the Report status screen, so the notification says what the screen says. */
    fun statusAlert(incident: Incident): CitizenAlert {
        val status = incident.statusEnum()
        return CitizenAlert(incident.id, CitizenAlertKind.STATUS, statusHeadline(status), statusDetail(status))
    }

    /** One notification per incident per snapshot: the newest reply, or a count when several arrived. */
    fun replyAlert(incidentId: String, replies: List<Message>): CitizenAlert? {
        val last = replies.lastOrNull() ?: return null
        val title = if (replies.size == 1) {
            "Reply from ${last.senderName.trim().ifBlank { "a responder" }}"
        } else {
            "${replies.size} new replies from responders"
        }
        return CitizenAlert(incidentId, CitizenAlertKind.REPLY, title, last.messageText.trim().take(MAX_REPLY_CHARS))
    }

    /** True when the person is already looking at the screen this alert is about. */
    fun isSuppressed(alert: CitizenAlert, viewing: Viewing): Boolean {
        if (viewing.incidentId != alert.incidentId) return false
        return when (alert.kind) {
            CitizenAlertKind.STATUS -> viewing.screen == ViewedScreen.STATUS
            CitizenAlertKind.REPLY -> viewing.screen == ViewedScreen.CHAT
        }
    }
}

/**
 * Turns snapshots of MY reports into status-change alerts. Not thread-safe: feed it from one
 * coroutine (the watcher does).
 *
 * Baseline rule, the same as the responder AlertTracker: a snapshot built only from the copy saved
 * on the phone ([fromCache]) can be old, so it never alerts and never becomes the baseline. The
 * first SERVER snapshot is the baseline; later server snapshots are compared to the last one.
 * A report that appears for the first time (just sent) never alerts: it is the person's own.
 */
class CitizenStatusTracker {

    private var previous: Map<String, String>? = null

    fun onSnapshot(incidents: List<Incident>, fromCache: Boolean, nowMillis: Long): List<CitizenAlert> {
        if (fromCache) return emptyList()
        val old = previous
        previous = incidents.associate { it.id to it.status }
        if (old == null) return emptyList()
        return incidents
            .filter { incident ->
                val before = old[incident.id]
                before != null && before != incident.status &&
                    CitizenAlertRules.isFresh(incident.updatedAt, nowMillis)
            }
            .map { CitizenAlertRules.statusAlert(it) }
    }
}

/**
 * Turns snapshots of ONE report's chat into a reply alert. Same baseline rule as above. Only
 * messages from a responder or admin alert: never my own (including one still waiting to sync),
 * and never another citizen.
 */
class ReplyTracker(private val incidentId: String) {

    private var seen: Set<String>? = null

    fun onSnapshot(messages: List<Message>, fromCache: Boolean, myUid: String, nowMillis: Long): CitizenAlert? {
        if (fromCache) return null
        val old = seen
        seen = messages.map { it.id }.toSet()
        if (old == null) return null
        val replies = messages.filter {
            it.id !in old &&
                it.senderId != myUid &&
                Role.fromValue(it.senderRole) != Role.CITIZEN &&
                CitizenAlertRules.isFresh(it.sentAt, nowMillis)
        }
        return CitizenAlertRules.replyAlert(incidentId, replies)
    }
}

/** Only open reports get a chat listener: it keeps the number of listeners small. */
object CitizenWatchRules {
    const val MAX_WATCHED_THREADS = 10

    fun threadsToWatch(incidents: List<Incident>): Set<String> =
        incidents
            .filter { it.statusEnum().isActive }
            .sortedByDescending { it.submittedAt?.seconds ?: Long.MAX_VALUE } // a just-sent report has no time yet: newest
            .take(MAX_WATCHED_THREADS)
            .map { it.id }
            .toSet()
}

/** Where a citizen notification opens. */
enum class CitizenScreen(val value: String) {
    STATUS("status"), CHAT("chat");

    companion object {
        fun fromValue(v: String?): CitizenScreen? = entries.firstOrNull { it.value == v }
    }
}

data class CitizenTarget(val incidentId: String, val screen: CitizenScreen)

/**
 * "Open this report's screen as soon as possible", set when a citizen notification is tapped. Same
 * idea as [PendingAlertTarget] and kept apart from it, so the responder rules stay untouched.
 * Lives for the whole app process, so it survives the trip through Splash.
 */
object PendingCitizenTarget {
    private val _target = MutableStateFlow<CitizenTarget?>(null)
    val target: StateFlow<CitizenTarget?> = _target.asStateFlow()

    fun set(incidentId: String?, screen: CitizenScreen?) {
        if (!incidentId.isNullOrBlank() && screen != null) _target.value = CitizenTarget(incidentId, screen)
    }

    fun clear() {
        _target.value = null
    }
}

object CitizenTargetRules {
    /** Waits while Splash decides, opens in the citizen area, drops for everyone else (never fires later). */
    fun decide(place: TargetPlace): TargetAction = when (place) {
        TargetPlace.STARTING -> TargetAction.WAIT
        TargetPlace.CITIZEN_AREA -> TargetAction.OPEN
        TargetPlace.RESPONDER_AREA, TargetPlace.ELSEWHERE -> TargetAction.DROP
    }
}

/** The screen the citizen is on right now. Set by AppNavHost, read by the watcher. */
object CitizenViewing {
    @Volatile
    var current: Viewing = Viewing()
        private set

    fun set(viewing: Viewing) {
        current = viewing
    }

    fun clear() {
        current = Viewing()
    }
}
