package com.example.fires.util

import com.example.fires.data.model.Assignment
import com.example.fires.data.model.IncidentStatus
import com.example.fires.util.LocationShareRules.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationShareRulesTest {

    private val mine = Assignment(responderId = "r1", unit = "BFP")
    private val theirs = Assignment(responderId = "r2", unit = "BDRRMO")

    // ---- who may start, and for how long ----

    @Test fun onlyDispatchedKeepsSharing() {
        assertTrue(LocationShareRules.shouldKeepSharing(IncidentStatus.DISPATCHED))
        listOf(
            IncidentStatus.REPORTED, IncidentStatus.VERIFIED, IncidentStatus.ON_SCENE,
            IncidentStatus.RESOLVED, IncidentStatus.DISMISSED
        ).forEach { assertFalse(it.value, LocationShareRules.shouldKeepSharing(it)) }
    }

    @Test fun assignedResponderCanStartWhenDispatched() {
        assertTrue(LocationShareRules.canStart(IncidentStatus.DISPATCHED, listOf(theirs, mine), "r1"))
    }

    @Test fun unassignedResponderCannotStart() {
        assertFalse(LocationShareRules.canStart(IncidentStatus.DISPATCHED, listOf(theirs), "r1"))
        assertFalse(LocationShareRules.canStart(IncidentStatus.DISPATCHED, emptyList(), "r1"))
    }

    @Test fun nobodySignedInCannotStart() {
        assertFalse(LocationShareRules.canStart(IncidentStatus.DISPATCHED, listOf(mine), null))
        assertFalse(LocationShareRules.canStart(IncidentStatus.DISPATCHED, listOf(Assignment()), ""))
    }

    @Test fun cannotStartOutsideDispatched() {
        assertFalse(LocationShareRules.canStart(IncidentStatus.VERIFIED, listOf(mine), "r1"))
        assertFalse(LocationShareRules.canStart(IncidentStatus.ON_SCENE, listOf(mine), "r1"))
        assertFalse(LocationShareRules.canStart(IncidentStatus.RESOLVED, listOf(mine), "r1"))
    }

    // ---- how often a position is written ----

    private val start = Fix(14.5995, 120.9842, atMillis = 100_000L)

    @Test fun firstFixIsAlwaysWritten() {
        assertTrue(LocationShareRules.shouldWrite(null, 14.5995, 120.9842, 100_000L))
    }

    @Test fun standingStillIsSkippedUntilTheHeartbeat() {
        assertFalse(LocationShareRules.shouldWrite(start, 14.5995, 120.9842, 110_000L))
        assertFalse(LocationShareRules.shouldWrite(start, 14.5995, 120.9842, 129_000L))
        assertTrue(LocationShareRules.shouldWrite(start, 14.5995, 120.9842, 130_000L))
    }

    @Test fun movingFarEnoughIsWritten() {
        // About 33 m north (0.0003 degrees of latitude), 10 s later.
        assertTrue(LocationShareRules.shouldWrite(start, 14.5998, 120.9842, 110_000L))
    }

    @Test fun smallMoveIsSkipped() {
        // About 11 m north.
        assertFalse(LocationShareRules.shouldWrite(start, 14.5996, 120.9842, 110_000L))
    }

    @Test fun bigJumpTooSoonAfterTheLastWriteIsSkipped() {
        assertFalse(LocationShareRules.shouldWrite(start, 14.5998, 120.9842, 102_000L))
    }

    @Test fun distanceIsAboutRight() {
        // One degree of latitude is about 111.2 km.
        val d = LocationShareRules.distanceMeters(14.0, 120.0, 15.0, 120.0)
        assertEquals(111_195.0, d, 300.0)
        assertEquals(0.0, LocationShareRules.distanceMeters(14.5, 121.0, 14.5, 121.0), 0.001)
    }

    // ---- heading and speed ----

    @Test fun unknownHeadingOrSpeedBecomesNull() {
        assertNull(LocationShareRules.cleanOrNull(90f, hasValue = false))
        assertNull(LocationShareRules.cleanOrNull(-1f, hasValue = true))
        assertNull(LocationShareRules.cleanOrNull(Float.NaN, hasValue = true))
        assertEquals(90.0, LocationShareRules.cleanOrNull(90f, hasValue = true)!!, 0.0001)
    }

    // ---- H5b: GPS lost during a response ----

    @Test fun gpsCountsAsLostOnlyWhenUnavailableAndTheSwitchIsOff() {
        assertTrue(LocationShareRules.gpsLost(isLocationAvailable = false, isLocationSwitchOn = false))
    }

    @Test fun noSignalWithTheSwitchOnIsNotGpsOff() {
        // Indoors or in a tunnel: the responder cannot fix this by "turning GPS on".
        assertFalse(LocationShareRules.gpsLost(isLocationAvailable = false, isLocationSwitchOn = true))
    }

    @Test fun availableLocationIsNeverLost() {
        assertFalse(LocationShareRules.gpsLost(isLocationAvailable = true, isLocationSwitchOn = true))
        assertFalse(LocationShareRules.gpsLost(isLocationAvailable = true, isLocationSwitchOn = false))
    }

    @Test fun gpsOffWordingIsTheAgreedText() {
        assertEquals("GPS is off. Turn it on so the reporter can see you.", LocationShareRules.GPS_OFF_NOTE)
    }

    @Test fun stoppedNoticeSaysWhatHappenedAndWhatToDo() {
        assertEquals("Sharing stopped", LocationShareRules.STOPPED_TITLE)
        assertTrue(LocationShareRules.STOPPED_PERMISSION_TEXT.contains("permission"))
        assertTrue(LocationShareRules.STOPPED_PERMISSION_TEXT.contains("Open the app"))
    }
}
