package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.ResponderLocation
import com.example.fires.util.ResponderTrackingRules.Kind
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponderTrackingRulesTest {

    private val now = 1_000_000_000L // milliseconds

    private fun responder(
        id: String = "r1",
        agoMillis: Long = 5_000L,
        eta: Int? = 360,
        distance: Int? = 2_400
    ) = ResponderLocation(
        id = id,
        latitude = 14.6,
        longitude = 120.98,
        etaSeconds = eta,
        distanceMeters = distance,
        updatedAt = Timestamp((now - agoMillis) / 1000, 0)
    )

    // ---- when there is a banner at all ----

    @Test fun nothingBeforeDispatchOrAfterClosing() {
        listOf(
            IncidentStatus.REPORTED, IncidentStatus.VERIFIED, IncidentStatus.RESOLVED, IncidentStatus.DISMISSED
        ).forEach { assertNull(it.value, ResponderTrackingRules.banner(it, listOf(responder()), now)) }
    }

    @Test fun onSceneSaysArrivedEvenWithoutLocations() {
        val banner = ResponderTrackingRules.banner(IncidentStatus.ON_SCENE, emptyList(), now)!!
        assertEquals(Kind.ARRIVED, banner.kind)
        assertEquals("Arrived. Responders are at the scene.", banner.text)
    }

    @Test fun dispatchedWithNobodySharingWaits() {
        val banner = ResponderTrackingRules.banner(IncidentStatus.DISPATCHED, emptyList(), now)!!
        assertEquals(Kind.WAITING, banner.kind)
    }

    // ---- live ----

    @Test fun liveBannerGivesTheEta() {
        val banner = ResponderTrackingRules.banner(IncidentStatus.DISPATCHED, listOf(responder()), now)!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("Responder is on the way, arriving in about 6 min", banner.text)
    }

    @Test fun noRouteYetStillSaysOnTheWay() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED, listOf(responder(eta = null, distance = null)), now
        )!!
        assertEquals("Responder is on the way", banner.text)
    }

    @Test fun straightLineFallbackGivesNoEtaSoTheBannerHasNoTime() {
        // What the service writes while only a straight-line estimate exists (H5d).
        val line = RouteRules.straightLine(
            com.example.fires.ui.common.LatLon(14.5895, 120.9842),
            com.example.fires.ui.common.LatLon(14.5995, 120.9842)
        )
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder(eta = RouteRules.etaSecondsToWrite(line), distance = RouteRules.distanceMetersToWrite(line))),
            now
        )!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("Responder is on the way", banner.text)
    }

    @Test fun veryCloseSaysArrivingNow() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED, listOf(responder(eta = 20, distance = 30)), now
        )!!
        assertEquals("Responder is arriving now", banner.text)
    }

    @Test fun severalRespondersUseTheNearest() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("a", eta = 900), responder("b", eta = 300), responder("c", eta = null)),
            now
        )!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("3 responders are on the way, the nearest arriving in about 5 min", banner.text)
    }

    // ---- stale ----

    @Test fun exactlyAMinuteOldIsStale() {
        assertTrue(ResponderTrackingRules.isFresh(responder(agoMillis = 59_000L), now))
        assertFalse(ResponderTrackingRules.isFresh(responder(agoMillis = 60_000L), now))
    }

    @Test fun staleLocationShowsAgeNotAnEta() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED, listOf(responder(agoMillis = 125_000L)), now
        )!!
        assertEquals(Kind.STALE, banner.kind)
        assertEquals("Location last updated 2 min ago", banner.text)
    }

    @Test fun staleAgeUsesTheNewestOfSeveral() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("a", agoMillis = 600_000L), responder("b", agoMillis = 180_000L)),
            now
        )!!
        assertEquals("Location last updated 3 min ago", banner.text)
    }

    @Test fun oneFreshResponderMakesItLiveEvenIfAnotherIsStale() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("old", agoMillis = 300_000L, eta = 100), responder("new", eta = 480)),
            now
        )!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("Responder is on the way, arriving in about 8 min", banner.text)
    }

    @Test fun aWriteStillWaitingForTheServerCountsAsFresh() {
        val pending = responder().copy(updatedAt = null)
        assertTrue(ResponderTrackingRules.isFresh(pending, now))
    }

    // ---- H5a: several responders in the banner ----

    @Test fun twoFreshRespondersUseTheSmallerEta() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("a", eta = 900), responder("b", eta = 300)),
            now
        )!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("2 responders are on the way, the nearest arriving in about 5 min", banner.text)
    }

    @Test fun aStaleResponderIsNotCountedOrUsedInTheBanner() {
        // "stale" has the smaller ETA, but it is old, so only the fresh one speaks.
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("fresh", eta = 600), responder("stale", agoMillis = 200_000L, eta = 60)),
            now
        )!!
        assertEquals(Kind.LIVE, banner.kind)
        assertEquals("Responder is on the way, arriving in about 10 min", banner.text)
    }

    @Test fun oneWithoutAnEtaStillGivesOnTheWay() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("fresh", eta = null, distance = null), responder("stale", agoMillis = 200_000L, eta = 60)),
            now
        )!!
        assertEquals("Responder is on the way", banner.text)
    }

    @Test fun severalWithoutAnyEtaStillGiveOnTheWay() {
        val banner = ResponderTrackingRules.banner(
            IncidentStatus.DISPATCHED,
            listOf(responder("a", eta = null, distance = null), responder("b", eta = null, distance = null)),
            now
        )!!
        assertEquals("2 responders are on the way", banner.text)
    }

    // ---- H5a: marker and legend labels ----

    @Test fun aLoneResponderIsJustCalledResponder() {
        val labels = ResponderTrackingRules.markerLabels(listOf(responder("a")), now)
        assertEquals(1, labels.size)
        assertEquals("Responder", labels[0].text)
        assertTrue(labels[0].fresh)
    }

    @Test fun severalRespondersAreNumberedInIdOrder() {
        // Handed over out of order on purpose: numbering follows the id, not the list.
        val labels = ResponderTrackingRules.markerLabels(
            listOf(responder("c"), responder("a"), responder("b")), now
        )
        assertEquals(listOf("a", "b", "c"), labels.map { it.responderId })
        assertEquals(listOf("Responder 1", "Responder 2", "Responder 3"), labels.map { it.text })
        assertEquals(listOf(0, 1, 2), labels.map { it.position })
    }

    @Test fun aRespondersNumberDoesNotChangeWhenTheListOrderChanges() {
        val one = ResponderTrackingRules.markerLabels(listOf(responder("a"), responder("b")), now)
        val flipped = ResponderTrackingRules.markerLabels(listOf(responder("b"), responder("a")), now)
        assertEquals(one, flipped)
    }

    @Test fun aStaleResponderShowsWhenItWasLastSeen() {
        val labels = ResponderTrackingRules.markerLabels(
            listOf(responder("a"), responder("b", agoMillis = 125_000L)), now
        )
        assertEquals("Responder 1", labels[0].text)
        assertEquals("Responder 2 (last seen 2 min ago)", labels[1].text)
        assertTrue(labels[0].fresh)
        assertFalse(labels[1].fresh)
    }

    @Test fun aLoneStaleResponderShowsLastSeenToo() {
        val labels = ResponderTrackingRules.markerLabels(listOf(responder("a", agoMillis = 190_000L)), now)
        assertEquals("Responder (last seen 3 min ago)", labels[0].text)
    }

    @Test fun noRespondersGiveNoLabels() {
        assertTrue(ResponderTrackingRules.markerLabels(emptyList(), now).isEmpty())
    }
}
