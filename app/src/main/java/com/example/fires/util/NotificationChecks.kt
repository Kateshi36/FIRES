package com.example.fires.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Notification permission helpers. Only Android 13+ has a notification permission to ask for. */
object NotificationChecks {

    /** Android 13 (API 33) introduced the runtime notification permission. Older phones just allow it. */
    val isRuntimePermissionRequired: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * True only when this phone has the permission AND it is not granted yet. On Android 12 and
     * older this is always false, so the notifications step never appears (it is skipped silently).
     */
    fun needsPrompt(context: Context): Boolean =
        isRuntimePermissionRequired && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
}
