package com.example.fires.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * The notification channels: three for responder alerts (F1.4), two for citizens (F2). Android 8.0+ requires every
 * notification to belong to a channel, and minSdk is 26, so no version check is needed.
 *
 * Created from FiresApp on every app start. Creating a channel that already exists changes
 * nothing, so this is safe to repeat. IMPORTANT: once a channel exists on a phone, the app can no
 * longer change its importance or sound (only the user can, in system settings). To change one
 * later, give it a NEW id here; the old channel then stays on phones that already have it.
 */
object AlertChannels {

    /** The permanent "Alerts are on" notification that keeps the service alive. Silent. */
    const val SERVICE = "alerts_service"

    /** A new fire report. Sound and heads-up, because this is the one that must not be missed. */
    const val NEW_INCIDENT = "alerts_new_incident"

    /** An existing incident changed status. Shows up, but does not interrupt. */
    const val STATUS_CHANGE = "alerts_status_change"

    /** A report I sent changed status ("Help is on the way"). Time-sensitive, so it pops up. */
    const val CITIZEN_STATUS = "citizen_status"

    /** A responder wrote in the chat of my report. Pops up, because they may be asking something urgent. */
    const val CITIZEN_REPLY = "citizen_reply"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val service = NotificationChannel(
            SERVICE, "Alerts are on", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "The ongoing notification shown while F.I.R.E.S. is watching for new fire reports."
            setShowBadge(false)
        }

        val newIncident = NotificationChannel(
            NEW_INCIDENT, "New fire reports", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "A new fire report was sent. Plays a sound and pops up on screen."
            enableVibration(true)
            setShowBadge(true)
        }

        val statusChange = NotificationChannel(
            STATUS_CHANGE, "Incident updates", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "An incident was verified, dispatched, or reached the scene."
            setShowBadge(true)
        }

        val citizenStatus = NotificationChannel(
            CITIZEN_STATUS, "Report updates", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "A report you sent was verified, dispatched, reached by responders, or closed."
            setShowBadge(true)
        }

        val citizenReply = NotificationChannel(
            CITIZEN_REPLY, "Replies from responders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "A responder replied in the chat of one of your reports."
            setShowBadge(true)
        }

        manager.createNotificationChannels(
            listOf(service, newIncident, statusChange, citizenStatus, citizenReply)
        )
    }
}
