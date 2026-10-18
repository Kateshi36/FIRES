package com.example.fires.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.fires.ui.common.LatLon
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the phone's current position (Google's Fused Location Provider).
 * Never throws: when permission or the GPS switch is off, or no position arrives in time,
 * the answer is simply null and the caller falls back to the manual map pin.
 */
object GpsFix {
    private const val TIMEOUT_MS = 12_000L

    @SuppressLint("MissingPermission") // checked just below; SecurityException is also caught
    suspend fun current(context: Context): LatLon? {
        if (!LocationChecks.hasLocationPermission(context) || !LocationChecks.isLocationEnabled(context)) {
            return null
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        // A fresh reading first. If none arrives in time, fall back to the last known position.
        val fresh = safely {
            withTimeoutOrNull(TIMEOUT_MS) {
                val cancel = CancellationTokenSource()
                try {
                    client.getCurrentLocation(priority, cancel.token).await()
                } finally {
                    cancel.cancel()
                }
            }
        }
        val location = fresh ?: safely { client.lastLocation.await() }
        return location?.let { LatLon(it.latitude, it.longitude) }
    }

    private suspend fun <T> safely(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e // the screen was closed: let the coroutine stop
    } catch (_: Exception) {
        null
    }
}
