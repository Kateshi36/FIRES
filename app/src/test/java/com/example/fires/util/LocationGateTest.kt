package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** C8: which prompt the permission screen shows. */
class LocationGateTest {

    private fun gate(
        hasPermission: Boolean = false,
        gpsEnabled: Boolean = true,
        wasDenied: Boolean = false,
        canAskAgain: Boolean = true
    ) = resolveLocationGate(hasPermission, gpsEnabled, wasDenied, canAskAgain)

    @Test fun noPermission_notAskedYet_asksForPermission() =
        assertEquals(LocationGate.AskPermission, gate())

    @Test fun denied_canStillAsk_offersTryAgain() =
        assertEquals(LocationGate.PermissionDenied(true), gate(wasDenied = true, canAskAgain = true))

    @Test fun denied_forGood_onlySettingsWork() =
        assertEquals(LocationGate.PermissionDenied(false), gate(wasDenied = true, canAskAgain = false))

    @Test fun permissionGranted_gpsOff_promptsForGps() =
        assertEquals(LocationGate.GpsOff, gate(hasPermission = true, gpsEnabled = false))

    @Test fun permissionGranted_gpsOn_isReady() =
        assertEquals(LocationGate.Ready, gate(hasPermission = true, gpsEnabled = true))

    @Test fun grantedLaterInSettings_afterADenial_movesOn() {
        // Denied earlier, then the person allowed it from Settings and came back.
        assertEquals(LocationGate.Ready, gate(hasPermission = true, wasDenied = true, canAskAgain = false))
        assertEquals(LocationGate.GpsOff, gate(hasPermission = true, gpsEnabled = false, wasDenied = true))
    }

    @Test fun permissionIsCheckedBeforeGps() {
        // Both missing: fix the permission first.
        assertEquals(LocationGate.AskPermission, gate(hasPermission = false, gpsEnabled = false))
        assertEquals(
            LocationGate.PermissionDenied(true),
            gate(hasPermission = false, gpsEnabled = false, wasDenied = true)
        )
    }
}
