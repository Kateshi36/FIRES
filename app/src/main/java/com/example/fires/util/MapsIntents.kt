package com.example.fires.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands the destination to Google Maps for voice turn-by-turn navigation. */
object MapsIntents {

    private const val GOOGLE_MAPS = "com.google.android.apps.maps"

    /**
     * Opens Google Maps navigation to the point. Without the app it opens the same directions in
     * the browser. With neither, nothing happens (the route is still on F.I.R.E.S.'s own map).
     */
    fun openNavigation(context: Context, latitude: Double, longitude: Double) {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse(RouteRules.googleMapsNavUri(latitude, longitude)))
            .setPackage(GOOGLE_MAPS)
        try {
            context.startActivity(app)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(RouteRules.googleMapsWebUrl(latitude, longitude)))
                )
            } catch (_: ActivityNotFoundException) {
                // No maps app and no browser. Nothing more to try.
            }
        }
    }
}
