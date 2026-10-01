package com.example.fires.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.fires.MainActivity
import com.example.fires.R
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.util.AlertChannels
import com.example.fires.util.AlertTracker
import com.example.fires.util.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a live listener on the active incidents while the app is closed, and turns changes into
 * alerts (F1.5 + F1.6). Shows the permanent "Alerts are on" notification the whole time.
 *
 * Starting it from the background is not allowed on Android 12+, so [start] must be called while
 * the app is on screen (F1.11 does that when the responder area opens).
 */
class ResponderAlertService : Service() {

    // Cancelled in onDestroy, which also removes the Firestore listener.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listenJob: Job? = null
    private val tracker = AlertTracker()
    private val notifier by lazy { AlertNotifier(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Runs on every start call, and again with a null intent when Android restarts the service
     * after killing it (START_STICKY). So it must be safe to run repeatedly.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Go foreground FIRST. After startForegroundService() Android allows about 5 seconds
        //    for this, and crashes the app if it does not happen, even when we stop right after.
        //    Android 14+ also wants the type here, matching the one in the manifest.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildOngoingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Exception) {
            // Android 12+ refuses when the app is in the background; other causes are possible.
            Log.w(TAG, "Could not go foreground; stopping", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Only a signed-in person gets alerts.
        if (AuthRepository().currentUid == null) {
            Log.i(TAG, "Nobody is signed in; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (listenJob?.isActive == true) return // already listening: a second start changes nothing

        tracker.reset()
        listenJob = scope.launch {
            // The flow only ends by failing (a Firestore listener that errors is dead for good,
            // for example permission denied after the session ended) or by cancellation.
            // attempt() rethrows cancellation, so onDestroy stops this quietly.
            val result = attempt {
                IncidentRepository().observeActiveSnapshots().collect { snapshot ->
                    val events = tracker.onSnapshot(
                        incidents = snapshot.incidents,
                        fromCache = snapshot.fromCache,
                        pendingIds = snapshot.pendingIds,
                        nowMillis = System.currentTimeMillis()
                    )
                    notifier.post(events)
                }
            }
            Log.w(TAG, "Incident listener ended; stopping", result.exceptionOrNull())
            stopSelf()
        }
    }

    private fun buildOngoingNotification(): Notification {
        // Tapping it opens the app.
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlertChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Alerts are on")
            .setContentText("F.I.R.E.S. is watching for new fire reports.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Android 12+ would otherwise delay this notification by about 10 seconds.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel() // stops the collector, which removes the Firestore listener
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ResponderAlertService"

        // Incident alerts (AlertNotifier) use the incident id as the notification TAG, so this fixed
        // id can never collide with one of them.
        private const val NOTIFICATION_ID = 1

        /**
         * Call while the app is on screen (AppNavHost does, when the responder area opens). Safe
         * to call again while already running.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, ResponderAlertService::class.java)
                )
            } catch (e: Exception) {
                // Android 12+ throws if the app is not visible at that moment. Alerts are lost
                // until the next start, but the app must not crash because of it.
                Log.w(TAG, "Could not start the alert service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ResponderAlertService::class.java))
        }
    }
}
