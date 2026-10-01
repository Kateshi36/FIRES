package com.example.fires.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fires.R
import com.example.fires.util.AlertChannels
import com.example.fires.util.AlertIntents
import com.example.fires.util.CitizenAlert
import com.example.fires.util.CitizenAlertKind

/**
 * Posts the citizen's notifications (F2). The wording is already made by CitizenAlertRules; this
 * only builds and posts.
 */
class CitizenNotifier(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    /**
     * Posts nothing, and breaks nothing, when notifications are not allowed (permission denied on
     * Android 13+, or switched off in system settings). They can be allowed later without
     * restarting anything.
     */
    // areNotificationsEnabled() is checked first. Lint cannot see that.
    @SuppressLint("MissingPermission")
    fun post(alert: CitizenAlert) {
        if (!manager.areNotificationsEnabled()) {
            Log.i(TAG, "Notifications are off; skipped ${alert.kind} for ${alert.incidentId}")
            return
        }
        try {
            // Tag = kind + report id, one fixed id: a newer status or reply REPLACES the earlier
            // notification for that report instead of stacking. The prefix keeps these apart from
            // the responder alerts, whose tag is the bare incident id.
            manager.notify("${alert.kind.name.lowercase()}:${alert.incidentId}", NOTIFICATION_ID, build(alert))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was removed; skipped ${alert.incidentId}", e)
        }
    }

    private fun build(alert: CitizenAlert) = when (alert.kind) {
        CitizenAlertKind.STATUS -> builder(alert, AlertChannels.CITIZEN_STATUS)
            .setContentIntent(AlertIntents.openStatus(context, alert.incidentId))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        CitizenAlertKind.REPLY -> builder(alert, AlertChannels.CITIZEN_REPLY)
            .setContentIntent(AlertIntents.openChat(context, alert.incidentId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
    }

    private fun builder(alert: CitizenAlert, channel: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

    private companion object {
        const val TAG = "CitizenNotifier"
        const val NOTIFICATION_ID = 0
    }
}
