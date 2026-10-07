package com.example.fires.util

import com.example.fires.data.model.Route
import com.example.fires.data.model.RouteStep
import com.example.fires.ui.common.LatLon
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Turns an OSRM "route" response (geometries=geojson, steps=true) into a [Route]. Returns null for
 * anything that is not a usable route (no road between the two points, an error code, bad JSON),
 * so the caller never has to catch parsing exceptions.
 */
object OsrmParser {

    fun parse(body: String): Route? = try {
        val root = JSONObject(body)
        if (root.optString("code") != "Ok") null else toRoute(root.getJSONArray("routes").optJSONObject(0))
    } catch (_: JSONException) {
        null
    }

    private fun toRoute(route: JSONObject?): Route? {
        route ?: return null
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        // GeoJSON order is [longitude, latitude].
        val points = (0 until coordinates.length()).map { i ->
            val pair = coordinates.getJSONArray(i)
            LatLon(latitude = pair.getDouble(1), longitude = pair.getDouble(0))
        }
        if (points.size < 2) return null

        return Route(
            points = points,
            distanceMeters = route.getDouble("distance").roundToInt(),
            durationSeconds = route.getDouble("duration").roundToInt(),
            steps = steps(route.optJSONArray("legs"))
        )
    }

    private fun steps(legs: JSONArray?): List<RouteStep> {
        legs ?: return emptyList()
        val result = mutableListOf<RouteStep>()
        for (l in 0 until legs.length()) {
            val steps = legs.getJSONObject(l).optJSONArray("steps") ?: continue
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val maneuver = step.getJSONObject("maneuver")
                result += RouteStep(
                    instruction = RouteRules.instruction(
                        type = maneuver.optString("type"),
                        modifier = maneuver.optString("modifier").ifEmpty { null },
                        name = step.optString("name"),
                        exit = if (maneuver.has("exit")) maneuver.optInt("exit") else null
                    ),
                    distanceMeters = step.optDouble("distance", 0.0).roundToInt()
                )
            }
        }
        return result
    }
}
