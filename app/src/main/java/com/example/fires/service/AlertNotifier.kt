package com.example.fires.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fires.R
import com.example.fires.util.AlertChannels
import com.example.fires.util.AlertEvent
import com.example.fires.util.AlertIntents
import com.example.fires.util.AlertLevel

/**
 * Turns [AlertEvent]s into notifications (F1.7). The wording and the loud/quiet decision are
 * already made by AlertRules; this only builds and posts.
 */
class AlertNotifier(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /**
     * Posts every event. When notifications are not allowed (permission denied on Android 13+, or
     * switched off in system settings) nothing is posted and nothing breaks: the service keeps
     * running, and the person can allow notifications later without restarting anything.
     */
    fun post(events: List<AlertEvent>) {
        if (events.isEmpty()) return
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Notifications are off; skipped ${events.size} alert(s)")
            return
        }
        events.forEach { post(it) }
    }

    // areNotificationsEnabled() was checked in post(events) above. Lint cannot see that.
    @SuppressLint("MissingPermission")
    private fun post(event: AlertEvent) {
        try {
            // The incident id is the notification's TAG, with one fixed id. Android identifies a
            // notification by (tag, id), so the same incident always lands on the same
            // notification: a status change REPLACES the earlier one instead of stacking a second.
            // (A tag, not a number made from the id, so two incidents can never collide and the
            // ongoing "Alerts are on" notification, which has no tag, can never be overwritten.)
            manager.notify(event.incidentId, INCIDENT_NOTIFICATION_ID, build(event))
        } catch (e: SecurityException) {
            // Permission taken away between the check and the post.
            Log.w(TAG, "Notification permission was removed; skipped ${event.incidentId}", e)
        }
    }

    private fun build(event: AlertEvent): Notification {
        val channel = when (event.level) {
            AlertLevel.LOUD -> AlertChannels.NEW_INCIDENT
            AlertLevel.QUIET -> AlertChannels.STATUS_CHANGE
        }
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(event.text)
            // Long addresses wrap instead of being cut off when the notification is expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.text))
            .setContentIntent(AlertIntents.openIncident(context, event.incidentId))
            .setAutoCancel(true)
            .build()
    }

    private companion object {
        const val TAG = "AlertNotifier"
        const val INCIDENT_NOTIFICATION_ID = 0
    }
}
