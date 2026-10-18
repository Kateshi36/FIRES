package com.example.fires.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.fires.MainActivity

/**
 * The tap target of an incident notification (F1.8): opens MainActivity carrying the incident id.
 * MainActivity reads it in F1.9 and the app navigates to that incident in F1.10.
 */
object AlertIntents {

    /** The extra that carries the incident id. MainActivity reads it with this same name. */
    const val EXTRA_INCIDENT_ID = "com.example.fires.extra.INCIDENT_ID"

    /** Present only on a citizen notification: which screen to open ("status" or "chat"). */
    const val EXTRA_CITIZEN_SCREEN = "com.example.fires.extra.CITIZEN_SCREEN"

    /** Responder tap: opens the incident detail. */
    fun openIncident(context: Context, incidentId: String): PendingIntent =
        build(context, incidentId, screen = null)

    /** Citizen tap on a status notification: opens the Report status screen. */
    fun openStatus(context: Context, incidentId: String): PendingIntent =
        build(context, incidentId, CitizenScreen.STATUS)

    /** Citizen tap on a reply notification: opens the chat. */
    fun openChat(context: Context, incidentId: String): PendingIntent =
        build(context, incidentId, CitizenScreen.CHAT)

    private fun build(context: Context, incidentId: String, screen: CitizenScreen?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // NEW_TASK: needed because the intent starts from a service, not from a screen.
            // SINGLE_TOP: if the app is already open on MainActivity, deliver the intent to it
            // (onNewIntent) instead of opening a second copy.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INCIDENT_ID, incidentId)
            if (screen != null) putExtra(EXTRA_CITIZEN_SCREEN, screen.value)

            // Android decides whether two PendingIntents are "the same" WITHOUT looking at extras.
            // Without something that differs per incident, every notification would share one
            // PendingIntent and all of them would open the last incident posted. The data URI
            // is what makes them different. (It is never matched against an intent-filter; the
            // intent is explicit.)
            // The status and chat taps of the same report must differ from each other too.
            data = Uri.Builder().scheme("fires").authority(screen?.value ?: "incident")
                .appendPath(incidentId).build()
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
