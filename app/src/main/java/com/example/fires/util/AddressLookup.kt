package com.example.fires.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.fires.ui.common.LatLon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Reverse geocoding: coordinates -> a readable address (Android's built-in Geocoder).
 * Needs internet on most phones. Never throws: any failure returns null, and the report form
 * then asks the person to type a landmark instead.
 */
object AddressLookup {

    suspend fun reverse(context: Context, point: LatLon): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocation(
                        point.latitude, point.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        }
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.latitude, point.longitude, 1) ?: emptyList()
                }
            }
            results.firstOrNull()?.let(::describe)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun describe(address: Address): String? {
        val fullLine = address.getAddressLine(0)
        if (!fullLine.isNullOrBlank()) return fullLine
        return listOfNotNull(address.thoroughfare, address.subLocality, address.locality)
            .joinToString(", ")
            .ifBlank { null }
    }
}
