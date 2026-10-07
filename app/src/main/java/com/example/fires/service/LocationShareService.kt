package com.example.fires.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.fires.MainActivity
import com.example.fires.R
import com.example.fires.data.model.statusEnum
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.ResponderLocationRepository
import com.example.fires.data.repository.RouteRepository
import com.example.fires.ui.common.LatLon
import com.example.fires.util.RouteRules
import com.example.fires.util.AlertChannels
import com.example.fires.util.LocationChecks
import com.example.fires.util.LocationShareRules
import com.example.fires.util.attempt
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shares the responder's live position while they are on a response (H2). It writes to
 * incidents/{id}/responderLocations/{uid}, which the citizen's tracker listens to (H4).
 *
 * Runs as a foreground service of type "location", so Android keeps it alive with the screen off
 * and shows the permanent "Sharing your location" notification (with a Stop button) the whole
 * time. It stops itself when:
 *   - the incident leaves DISPATCHED (on scene, resolved, dismissed, or stepped back),
 *   - the responder taps Stop, here or on the screen,
 *   - nobody is signed in, or location permission is gone.
 * When it ends, the responder's document is deleted so the marker leaves the citizen's map.
 *
 * H5b: if the phone's GPS is switched off while sharing, the service keeps running, says so in its
 * notification and in LocationShareState.gpsLost (so the screen can offer "Turn on GPS"), and
 * goes back to normal on its own when a fix arrives. If location permission is gone, it posts a
 * separate "Sharing stopped" notification before it stops. Note that Android usually kills the
 * whole app when the person revokes a permission in Settings. Then nothing can run to post
 * anything, the document is left behind, and the citizen's screen shows it as "last updated".
 *
 * H3: on each fix it also keeps a road route to the incident up to date (RouteRepository), shares
 * it with the screen through LocationShareState, and writes the ETA and distance with the position.
 *
 * It never restarts by itself after Android kills it (START_NOT_STICKY): sharing starts only from
 * a deliberate tap on "Start response". A document left behind by a kill goes stale, and the
 * citizen's screen shows it as "last updated" instead of a live position.
 *
 * Like the alert service, [start] must be called while the app is on screen (Android 12+).
 */
class LocationShareService : Service() {

    // Main thread, so location callbacks and state changes need no locking. Firestore and Fused
    // Location do their own work off the main thread. Cancelled in onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = ResponderLocationRepository()
    private val routes = RouteRepository()
    private val auth = AuthRepository()
    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var incidentId: String? = null
    private var responderId: String? = null
    private var callback: LocationCallback? = null
    private var statusWatch: Job? = null
    private var lastWrite: LocationShareRules.Fix? = null
    private var gpsLost = false                   // H5b: the phone's GPS switch is off right now

    // ---- Route to the incident (H3) ----
    private var target: LatLon? = null            // the incident's coordinates
    private var lastLocation: Location? = null    // latest fix, so a new route can be written at once
    private var lastRouteAsk: RouteRules.RouteFix? = null
    private var routeJob: Job? = null
    private var currentRoute: LiveRoute? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The Stop button on the notification. It was not started with startForegroundService(),
        // so there is no 5 second foreground deadline to meet here.
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Go foreground FIRST (Android crashes the app if a service started with
        //    startForegroundService() does not, even when it stops right after). Android 14+ wants
        //    the type here, matching the manifest, and throws if location permission is missing.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not go foreground; stopping", e)
            // Android 14+ refuses a location service without location permission. Say why.
            if (!LocationChecks.hasLocationPermission(this)) postSharingStoppedNotice()
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. Check what sharing needs. Anything missing means stop, not "share anyway".
        if (!LocationChecks.hasLocationPermission(this)) {
            stopBecausePermissionIsGone()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_INCIDENT_ID)
        val uid = auth.currentUid
        if (id.isNullOrBlank() || uid == null) {
            Log.w(TAG, "Missing incident or sign-in; stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // 3. Already sharing for this incident: a second start changes nothing.
        if (incidentId == id) return START_NOT_STICKY

        // Sharing for a different incident: finish that one first.
        if (incidentId != null) stopSharing(removeDocument = true)

        incidentId = id
        responderId = uid
        lastWrite = null
        lastRouteAsk = null
        currentRoute = null
        LocationShareState.set(id)
        watchStatus(id)
        if (!startLocationUpdates()) stopBecausePermissionIsGone()
        return START_NOT_STICKY
    }

    /** Stops sharing the moment the incident is no longer an active DISPATCHED response. */
    private fun watchStatus(id: String) {
        statusWatch?.cancel()
        statusWatch = scope.launch {
            // attempt() rethrows cancellation, so a normal stop ends this quietly. The flow only
            // fails when the listener is dead for good (for example permission denied after the
            // session ended). Then sharing stops too: location must not outlive the response.
            val result = attempt {
                IncidentRepository().observe(id).collect { incident ->
                    // The destination for the route. The scene does not move, but a responder may
                    // correct the pin, so keep reading it.
                    if (incident != null) target = LatLon(incident.latitude, incident.longitude)
                    if (incident == null || !LocationShareRules.shouldKeepSharing(incident.statusEnum())) {
                        Log.i(TAG, "Incident $id is no longer dispatched; stopping")
                        stopSelf()
                    }
                }
            }
            Log.w(TAG, "Status listener ended; stopping", result.exceptionOrNull())
            stopSelf()
        }
    }

    /** @return false if location could not be requested (permission removed in the meantime). */
    @SuppressLint("MissingPermission") // checked in onStartCommand; SecurityException is caught below
    private fun startLocationUpdates(): Boolean {
        val hasFine = LocationChecks.hasFineLocationPermission(this)
        val request = LocationRequest.Builder(
            if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LocationShareRules.INTERVAL_MS
        )
            // Fixes may arrive a little sooner than the interval. shouldWrite() decides what is sent.
            .setMinUpdateIntervalMillis(LocationShareRules.INTERVAL_MS / 2)
            .build()

        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::onFix)
            }

            // Tells us when the phone can or cannot produce a position (GPS switched off, no signal).
            override fun onLocationAvailability(availability: LocationAvailability) {
                onAvailabilityChanged(availability.isLocationAvailable)
            }
        }
        return try {
            fused.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
            callback = newCallback
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission was removed", e)
            false
        }
    }

    private fun onFix(location: Location) {
        val id = incidentId ?: return
        val uid = responderId ?: return
        // The session may have ended while this service was still running.
        if (auth.currentUid != uid) {
            Log.i(TAG, "Signed-in person changed; stopping")
            stopSelf()
            return
        }

        if (!LocationChecks.hasLocationPermission(this)) {
            stopBecausePermissionIsGone()
            return
        }

        updateGpsLost(false) // a fix arrived, so location works again
        val now = System.currentTimeMillis()
        lastLocation = location
        // The screen's map follows every fix, written or not.
        LocationShareState.setPosition(LatLon(location.latitude, location.longitude))
        refreshRouteIfDue(id, location, now)

        if (!LocationShareRules.shouldWrite(lastWrite, location.latitude, location.longitude, now)) return
        writePosition(id, uid, location, now)
    }

    /** One write: the position plus the ETA and distance from the latest route (null before the first). */
    private fun writePosition(id: String, uid: String, location: Location, now: Long) {
        lastWrite = LocationShareRules.Fix(location.latitude, location.longitude, now)
        val route = currentRoute?.takeIf { it.incidentId == id }?.route
        repository.update(
            incidentId = id,
            responderId = uid,
            latitude = location.latitude,
            longitude = location.longitude,
            heading = LocationShareRules.cleanOrNull(location.bearing, location.hasBearing()),
            speed = LocationShareRules.cleanOrNull(location.speed, location.hasSpeed()),
            // H5d: a straight-line estimate has no ETA, so these are null and the citizen's banner
            // reads "Responder is on the way" without a time.
            etaSeconds = RouteRules.etaSecondsToWrite(route),
            distanceMeters = RouteRules.distanceMetersToWrite(route)
        ).addOnFailureListener { e -> Log.w(TAG, "Could not write the position", e) }
    }

    /**
     * Asks the routing service for a fresh route when one is due (first fix, moved about 200 m, or
     * about 30 seconds old). One request at a time. A failure keeps the previous route, and the
     * next fix after the minimum gap simply tries again.
     */
    private fun refreshRouteIfDue(id: String, location: Location, now: Long) {
        val destination = target ?: return
        if (routeJob?.isActive == true) return
        if (!RouteRules.shouldRefresh(lastRouteAsk, location.latitude, location.longitude, now)) return

        lastRouteAsk = RouteRules.RouteFix(location.latitude, location.longitude, now)
        val from = LatLon(location.latitude, location.longitude)
        routeJob = scope.launch {
            val result = attempt { routes.route(from, destination) }
            val route = result.getOrNull()
            if (route == null) {
                Log.w(TAG, "Could not get a route", result.exceptionOrNull())
                fallBackToStraightLine(id, from, destination)
                return@launch
            }
            // The responder may have stopped while the request was running.
            if (incidentId != id) return@launch
            val live = LiveRoute(id, route, System.currentTimeMillis())
            currentRoute = live
            LocationShareState.setRoute(live)

            // Send the new ETA right away instead of waiting for the next scheduled position.
            val uid = responderId
            val latest = lastLocation
            if (uid != null && latest != null) writePosition(id, uid, latest, System.currentTimeMillis())
        }
    }

    /**
     * H5d: the route could not be fetched. Unless a recent real route is still on screen, show a
     * straight line from the responder to the scene so the map is never empty. The next real
     * route replaces it (see the success branch above). It is not written to Firestore as an
     * ETA: see [RouteRules.etaSecondsToWrite].
     */
    private fun fallBackToStraightLine(id: String, from: LatLon, to: LatLon) {
        // The responder may have stopped while the request was running.
        if (incidentId != id) return
        val current = currentRoute?.takeIf { it.incidentId == id }
        val now = System.currentTimeMillis()
        if (!RouteRules.needsStraightLine(current?.route, current?.computedAtMillis, now)) return

        // An old real route's ETA is still in Firestore: clear it right away instead of waiting
        // for the next scheduled position.
        val replacesRealRoute = current?.route?.isEstimate == false
        val live = LiveRoute(id, RouteRules.straightLine(from, to), now)
        currentRoute = live
        LocationShareState.setRoute(live)
        val uid = responderId
        val latest = lastLocation
        if (replacesRealRoute && uid != null && latest != null) writePosition(id, uid, latest, now)
    }

    // ---- H5b: GPS or permission lost during a response ----

    private fun onAvailabilityChanged(isAvailable: Boolean) {
        if (incidentId == null) return
        if (!LocationChecks.hasLocationPermission(this)) {
            stopBecausePermissionIsGone()
            return
        }
        updateGpsLost(LocationShareRules.gpsLost(isAvailable, LocationChecks.isLocationEnabled(this)))
    }

    /** Tells the screen, and rewrites the ongoing notification's text in place (same id, no new sound). */
    private fun updateGpsLost(lost: Boolean) {
        if (lost == gpsLost) return
        gpsLost = lost
        LocationShareState.setGpsLost(lost)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(lost))
    }

    /** Says so first, then stops, instead of the responder finding out only when the citizen asks. */
    private fun stopBecausePermissionIsGone() {
        Log.w(TAG, "Location permission is missing; stopping")
        postSharingStoppedNotice()
        stopSelf()
    }

    private fun postSharingStoppedNotice() {
        val notice = NotificationCompat.Builder(this, AlertChannels.LOCATION_STOPPED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(LocationShareRules.STOPPED_TITLE)
            .setContentText(LocationShareRules.STOPPED_PERMISSION_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(LocationShareRules.STOPPED_PERMISSION_TEXT))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        try {
            // Quietly ignored by Android when notifications are not allowed. Nothing to handle.
            getSystemService(NotificationManager::class.java).notify(STOPPED_NOTIFICATION_ID, notice)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post the sharing-stopped notice", e)
        }
    }

    /** Stops location updates and the status listener, and (optionally) removes the document. */
    private fun stopSharing(removeDocument: Boolean) {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        statusWatch?.cancel()
        statusWatch = null
        routeJob?.cancel()
        routeJob = null
        target = null
        lastLocation = null
        lastRouteAsk = null
        currentRoute = null

        val id = incidentId
        val uid = responderId
        if (removeDocument && id != null && uid != null) {
            repository.remove(id, uid)
                .addOnFailureListener { e -> Log.w(TAG, "Could not remove the position", e) }
        }
        incidentId = null
        responderId = null
        lastWrite = null
        gpsLost = false
        LocationShareState.set(null)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(gpsIsOff: Boolean = false): Notification {
        val text = if (gpsIsOff) {
            LocationShareRules.GPS_OFF_NOTE
        } else {
            "Staff and the person who reported the fire can see where you are."
        }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlertChannels.LOCATION_SHARE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sharing your location")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop sharing", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Every way this service ends (stopSelf, stopService, sign-out) comes through here. */
    override fun onDestroy() {
        stopSharing(removeDocument = true)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationShareService"
        private const val EXTRA_INCIDENT_ID = "incident_id"
        private const val ACTION_STOP = "com.example.fires.action.STOP_LOCATION_SHARE"

        // The alert service uses 1. Incident alerts use the incident id as the notification TAG,
        // so these fixed ids cannot collide with them.
        private const val NOTIFICATION_ID = 2

        // "Sharing stopped" (H5b). Also fixed, and different from the ongoing one on purpose: it must
        // stay on screen after the ongoing notification goes away with the service.
        private const val STOPPED_NOTIFICATION_ID = 3

        /** Call while the app is on screen. Safe to call again for the same incident. */
        fun start(context: Context, incidentId: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LocationShareService::class.java)
                        .putExtra(EXTRA_INCIDENT_ID, incidentId)
                )
            } catch (e: Exception) {
                // Android 12+ throws if the app is not visible at that moment.
                Log.w(TAG, "Could not start location sharing", e)
            }
        }

        /** Stops sharing, whatever it was for. Does nothing when it is not running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationShareService::class.java))
        }
    }
}
