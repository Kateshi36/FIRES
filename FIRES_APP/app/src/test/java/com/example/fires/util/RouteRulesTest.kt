package com.example.fires.util

import com.example.fires.data.model.Route
import com.example.fires.ui.common.LatLon
import com.example.fires.util.RouteRules.RouteFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRulesTest {

    private val last = RouteFix(14.5995, 120.9842, atMillis = 100_000L)

    // ---- when to ask for a new route ----

    @Test fun firstRouteIsAlwaysAsked() {
        assertTrue(RouteRules.shouldRefresh(null, 14.5995, 120.9842, 100_000L))
    }

    @Test fun movingFarEnoughRefreshes() {
        // 0.0023 degrees of latitude is about 255 m. 12 s later.
        assertTrue(RouteRules.shouldRefresh(last, 14.6018, 120.9842, 112_000L))
    }

    @Test fun smallMoveDoesNotRefreshBeforeTheTimeLimit() {
        // About 55 m, 20 s later.
        assertFalse(RouteRules.shouldRefresh(last, 14.6000, 120.9842, 120_000L))
    }

    @Test fun oldRouteRefreshesEvenWhenStandingStill() {
        assertTrue(RouteRules.shouldRefresh(last, 14.5995, 120.9842, 130_000L))
    }

    @Test fun neverAsksTwiceWithinTheMinimumGap() {
        // Far away, but only 5 s after the last request.
        assertFalse(RouteRules.shouldRefresh(last, 14.6100, 120.9842, 105_000L))
    }

    // ---- wording ----

    @Test fun distanceLabels() {
        assertEquals("850 m", RouteRules.distanceLabel(850))
        assertEquals("850 m", RouteRules.distanceLabel(853))
        assertEquals("990 m", RouteRules.distanceLabel(994))
        assertEquals("1.0 km", RouteRules.distanceLabel(995))
        assertEquals("2.4 km", RouteRules.distanceLabel(2_412))
        assertEquals("12 km", RouteRules.distanceLabel(12_400))
    }

    @Test fun etaLabels() {
        assertEquals("less than 1 min", RouteRules.etaLabel(30))
        assertEquals("about 1 min", RouteRules.etaLabel(89))
        assertEquals("about 6 min", RouteRules.etaLabel(360))
        assertEquals("about 1 hr", RouteRules.etaLabel(3_600))
        assertEquals("about 1 hr 5 min", RouteRules.etaLabel(3_900))
    }

    @Test fun summaryJoinsDistanceAndTime() {
        assertEquals("2.4 km, about 6 min", RouteRules.summary(2_400, 360))
    }

    @Test fun closeToTheSceneSaysArriving() {
        assertEquals("Arriving", RouteRules.summary(30, 20))
    }

    // ---- turn instructions ----

    @Test fun turnsNameTheRoad() {
        assertEquals("Turn left onto Rizal St.", RouteRules.instruction("turn", "left", "Rizal St."))
        assertEquals("Bear right", RouteRules.instruction("turn", "slight right", ""))
        assertEquals("Turn right onto Mabini St.", RouteRules.instruction("end of road", "right", "Mabini St."))
        assertEquals("Make a U-turn", RouteRules.instruction("continue", "uturn", null))
    }

    @Test fun goingStraightSaysOn() {
        assertEquals("Continue straight on Mabini St.", RouteRules.instruction("new name", "straight", "Mabini St."))
        assertEquals("Continue straight", RouteRules.instruction("continue", null, ""))
    }

    @Test fun departAndArrive() {
        assertEquals("Head out on Rizal St.", RouteRules.instruction("depart", null, "Rizal St."))
        assertEquals("Head out", RouteRules.instruction("depart", null, ""))
        assertEquals("Arrive at the scene", RouteRules.instruction("arrive", null, ""))
        assertEquals("Arrive at the scene, on your left", RouteRules.instruction("arrive", "left", ""))
    }

    @Test fun roundaboutsUseTheExitNumber() {
        assertEquals(
            "At the roundabout, take exit 2 onto Mabini St.",
            RouteRules.instruction("roundabout", null, "Mabini St.", exit = 2)
        )
        assertEquals("Enter the roundabout", RouteRules.instruction("rotary", null, "", exit = null))
    }

    // ---- Google Maps hand-off ----

    @Test fun googleMapsLinks() {
        assertEquals(
            "google.navigation:q=14.5995,120.9842&mode=d",
            RouteRules.googleMapsNavUri(14.5995, 120.9842)
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=14.5995,120.9842&travelmode=driving",
            RouteRules.googleMapsWebUrl(14.5995, 120.9842)
        )
    }

    // ---- straight-line fallback (H5d) ----

    private val responder = LatLon(14.5895, 120.9842)
    private val scene = LatLon(14.5995, 120.9842) // 0.01 degrees of latitude: about 1,112 m due north

    @Test fun straightLineHasTwoPointsAndIsFlaggedAsAnEstimate() {
        val line = RouteRules.straightLine(responder, scene)
        assertEquals(listOf(responder, scene), line.points)
        assertTrue(line.isEstimate)
    }

    @Test fun straightLineDistanceIsTheDistanceBetweenThePoints() {
        val line = RouteRules.straightLine(responder, scene)
        assertEquals(1_112.0, line.distanceMeters.toDouble(), 3.0)
    }

    @Test fun straightLineHasNoEtaAndNoSteps() {
        val line = RouteRules.straightLine(responder, scene)
        assertEquals(0, line.durationSeconds)
        assertTrue(line.steps.isEmpty())
    }

    @Test fun straightLineFromTheSamePointIsZeroMetres() {
        val line = RouteRules.straightLine(scene, scene)
        assertEquals(0, line.distanceMeters)
        assertEquals(2, line.points.size)
    }

    @Test fun aRoadRouteIsNotAnEstimate() {
        assertFalse(Route(listOf(responder, scene), 1_200, 180).isEstimate)
    }

    @Test fun straightLineTextMatchesTheWording() {
        assertEquals(
            "About 1.2 km, straight line (no route available)",
            RouteRules.straightLineSummary(1_200)
        )
        assertEquals(
            "About 850 m, straight line (no route available)",
            RouteRules.straightLineSummary(850)
        )
    }

    @Test fun straightLineVeryCloseSaysArriving() {
        assertEquals("Arriving", RouteRules.straightLineSummary(RouteRules.ARRIVING_METERS))
    }

    @Test fun routeSummaryPicksTheRightText() {
        assertEquals(
            "About 1.1 km, straight line (no route available)",
            RouteRules.routeSummary(RouteRules.straightLine(responder, scene))
        )
        assertEquals("2.4 km, about 6 min", RouteRules.routeSummary(Route(listOf(responder, scene), 2_400, 360)))
    }

    // ---- when the fallback is used ----

    private val road = Route(listOf(responder, scene), 1_200, 180)
    private val line = RouteRules.straightLine(responder, scene)

    @Test fun noRouteAtAll_usesTheStraightLine() =
        assertTrue(RouteRules.needsStraightLine(null, null, 1_000_000L))

    @Test fun recentRealRoute_isKept() {
        val computedAt = 1_000_000L
        assertFalse(RouteRules.needsStraightLine(road, computedAt, computedAt + 60_000L))
        assertFalse(RouteRules.needsStraightLine(road, computedAt, computedAt + RouteRules.REAL_ROUTE_RECENT_MS))
    }

    @Test fun oldRealRoute_isReplacedByTheStraightLine() {
        val computedAt = 1_000_000L
        assertTrue(RouteRules.needsStraightLine(road, computedAt, computedAt + RouteRules.REAL_ROUTE_RECENT_MS + 1))
    }

    @Test fun anExistingStraightLine_isRedrawnFromTheNewPosition() =
        assertTrue(RouteRules.needsStraightLine(line, 1_000_000L, 1_010_000L))

    // ---- what the citizen is told (no ETA from an estimate) ----

    @Test fun anEstimateWritesNoEtaAndNoDistance() {
        assertNull(RouteRules.etaSecondsToWrite(line))
        assertNull(RouteRules.distanceMetersToWrite(line))
    }

    @Test fun aRoadRouteWritesItsEtaAndDistance() {
        assertEquals(180, RouteRules.etaSecondsToWrite(road))
        assertEquals(1_200, RouteRules.distanceMetersToWrite(road))
    }

    @Test fun noRouteWritesNothing() {
        assertNull(RouteRules.etaSecondsToWrite(null))
        assertNull(RouteRules.distanceMetersToWrite(null))
    }
}
