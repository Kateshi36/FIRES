package com.example.fires.data.repository

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import com.example.fires.util.OsrmParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The fastest road route between two points, from OSRM (OpenStreetMap data, free, no API key).
 * Uses the public demo server: fine for a capstone, but it is for light, non-commercial use, has
 * no uptime promise, and has no live traffic, so the times are estimates (see docs/LIMITATIONS.md).
 * For real use, host your own OSRM: it speaks the same API, so only [BASE_URL] changes. Another
 * provider such as OpenRouteService needs more than that: a key, and a parser for its different
 * answer format (OsrmParser reads OSRM's only). Only this class talks to the routing server.
 *
 * Throws on any failure (no connection, server error, no road between the points). Callers use
 * attempt { }, as everywhere else in the app.
 */
class RouteRepository {

    suspend fun route(from: LatLon, to: LatLon): Route = withContext(Dispatchers.IO) {
        // OSRM wants longitude first.
        val url = URL(
            "$BASE_URL/${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
                "?overview=full&geometries=geojson&steps=true"
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // The demo server asks every client to say who it is.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Routing server answered $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            OsrmParser.parse(body) ?: throw IOException("No route found between these points")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"
        const val USER_AGENT = "FIRES-Bagumbayan-Android (capstone project)"
        const val TIMEOUT_MS = 8_000
    }
}
