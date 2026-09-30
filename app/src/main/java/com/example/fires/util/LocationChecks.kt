package com.example.fires.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/** Android-facing helpers for location permission and the phone's location (GPS) switch. */
object LocationChecks {

    /** Both are requested together. On Android 12+ the person may choose "Approximate" (coarse). */
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /**
     * True if either precise or approximate location is allowed. We do not block on "Approximate":
     * in an emergency the person must still be able to report, and the report form lets them
     * move the map pin to the exact spot.
     */
    fun hasLocationPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Is the phone's location switch on? (Quick Settings > Location.) */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /** This app's page in Android Settings (Permissions > Location lives under it). */
    fun openAppSettings(context: Context) = safeStart(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )

    /** The phone's own Location settings screen. */
    fun openLocationSettings(context: Context) =
        safeStart(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    /**
     * Shows Google's one-tap "Turn on device location?" dialog, so the person does not have to
     * dig through Settings. If that dialog is not possible (no Google Play services, or it fails),
     * [onFallback] is called and the caller should open the Location settings screen instead.
     *
     * @param onAlreadyOn the switch was already on, nothing to ask
     */
    fun promptEnableLocation(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onAlreadyOn: () -> Unit,
        onFallback: () -> Unit
    ) {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .build()

        LocationServices.getSettingsClient(context)
            .checkLocationSettings(request)
            .addOnSuccessListener { onAlreadyOn() }
            .addOnFailureListener { error ->
                if (error is ResolvableApiException) {
                    try {
                        launcher.launch(IntentSenderRequest.Builder(error.resolution).build())
                    } catch (_: IntentSender.SendIntentException) {
                        onFallback()
                    }
                } else {
                    onFallback()
                }
            }
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some custom Android builds lack a settings page. Nothing more we can do.
        }
    }
}
