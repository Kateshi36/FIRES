package com.example.fires.util

import android.content.Context

/**
 * The two switches on the citizen Profile screen. Saved on this phone only. Both default to ON, so
 * nobody loses an alert by updating the app.
 */
object NotificationPrefs {
    private const val FILE = "fires_notification_prefs"
    private const val KEY_STATUS = "citizen_status"
    private const val KEY_REPLIES = "citizen_replies"

    fun statusEnabled(context: Context): Boolean = read(context, KEY_STATUS)
    fun repliesEnabled(context: Context): Boolean = read(context, KEY_REPLIES)

    fun setStatusEnabled(context: Context, on: Boolean) = write(context, KEY_STATUS, on)
    fun setRepliesEnabled(context: Context, on: Boolean) = write(context, KEY_REPLIES, on)

    private const val KEY_NOTICES_SEEN = "responder_notices_seen_ms"

    /** The newest assignment notice the responder has opened in the bell list (0 = none yet). */
    fun noticesSeenMs(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_NOTICES_SEEN, 0L)

    fun markNoticesSeen(context: Context, millis: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY_NOTICES_SEEN, millis).apply()
    }

    /** Whether a notification of this kind may be shown. */
    fun allows(context: Context, kind: CitizenAlertKind): Boolean = when (kind) {
        CitizenAlertKind.STATUS -> statusEnabled(context)
        CitizenAlertKind.REPLY -> repliesEnabled(context)
    }

    private fun read(context: Context, key: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(key, true)

    private fun write(context: Context, key: String, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    }
}
