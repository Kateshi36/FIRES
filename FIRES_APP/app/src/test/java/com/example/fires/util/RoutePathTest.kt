package com.example.fires.util

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The responder's route, thinned and shared so the citizen's map can draw the same path. */
class RoutePathTest {

    private fun route(points: List<LatLon>, estimate: Boolean = false) =
        Route(points = points, distanceMeters = 1000, durationSeconds = 120, isEstimate = estimate)

    @Test fun aStraightRoadCollapsesToItsTwoEnds() {
        val line = (0..50).map { LatLon(14.6000 + it * 0.0001, 120.9842) }
        val shared = RouteRules.pathToShare(route(line))!!
        assertEquals(4, shared.size)
        assertEquals(14.6, shared[0], 0.00001)
        assertEquals(14.605, shared[2], 0.00001)
    }

    @Test fun aCornerIsKept() {
        val points = listOf(
            LatLon(14.6000, 120.9842),
            LatLon(14.6010, 120.9842),
            LatLon(14.6020, 120.9842), // the road turns here
            LatLon(14.6020, 120.9862),
            LatLon(14.6020, 120.9882)
        )
        val shared = RouteRules.pathToShare(route(points))!!
        assertEquals(6, shared.size) // three points: start, the corner, the end
        assertEquals(14.6020, shared[2], 0.00001)
        assertEquals(120.9842, shared[3], 0.00001)
    }

    @Test fun aStraightLineEstimateIsNeverShared() {
        assertNull(RouteRules.pathToShare(route(listOf(LatLon(14.6, 120.98), LatLon(14.61, 120.99)), estimate = true)))
        assertNull(RouteRules.pathToShare(null))
    }

    @Test fun aLongWindingRouteStaysWithinTheLimit() {
        val winding = (0..3000).map {
            LatLon(14.6 + it * 0.00001, 120.98 + 0.002 * Math.sin(it / 7.0))
        }
        val shared = RouteRules.pathToShare(route(winding))!!
        assertTrue(shared.size <= RouteRules.MAX_SHARED_PATH_NUMBERS)
        assertEquals(0, shared.size % 2)
    }

    @Test fun theCitizenReadsPairsBackAsPoints() {
        val points = RouteRules.pathFromShared(listOf(14.6, 120.98, 14.61, 120.99))
        assertEquals(2, points.size)
        assertEquals(LatLon(14.61, 120.99), points[1])
    }

    @Test fun malformedPathsGiveNothing() {
        assertTrue(RouteRules.pathFromShared(null).isEmpty())
        assertTrue(RouteRules.pathFromShared(listOf(14.6, 120.98)).isEmpty())
        assertTrue(RouteRules.pathFromShared(listOf(14.6, 120.98, 14.61)).isEmpty())
    }

    @Test fun standingStillAddsNoTrailPoint() {
        val start = listOf(LatLon(14.6000, 120.9842))
        assertEquals(1, RouteRules.appendToTrail(start, LatLon(14.60001, 120.9842)).size)
    }

    @Test fun movingFarEnoughAddsATrailPoint() {
        val start = listOf(LatLon(14.6000, 120.9842))
        assertEquals(2, RouteRules.appendToTrail(start, LatLon(14.6005, 120.9842)).size)
    }

    @Test fun theTrailKeepsOnlyTheNewestPoints() {
        var trail = emptyList<LatLon>()
        for (i in 0 until 200) trail = RouteRules.appendToTrail(trail, LatLon(14.6 + i * 0.001, 120.98))
        assertEquals(RouteRules.TRAIL_MAX_POINTS, trail.size)
        assertEquals(14.6 + 199 * 0.001, trail.last().latitude, 0.000001)
        assertEquals(RouteRules.TRAIL_MAX_POINTS * 2, RouteRules.trailToShare(trail)!!.size)
    }

    @Test fun aSinglePointIsNoTrail() {
        assertNull(RouteRules.trailToShare(listOf(LatLon(14.6, 120.98))))
    }
}
