package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** C8 items 4 and 5: notifications step, and "a denial never traps the person". */
class PermissionStepTest {

    private fun step(
        gate: LocationGate = LocationGate.Ready,
        locationSkipped: Boolean = false,
        notificationsNeeded: Boolean = false,
        notificationsAnswered: Boolean = false
    ) = resolvePermissionStep(gate, locationSkipped, notificationsNeeded, notificationsAnswered)

    // ---------- Item 4: notifications only on Android 13+ ----------

    @Test fun oldPhone_locationReady_skipsNotificationsSilently() =
        assertEquals(PermissionStep.Done, step(notificationsNeeded = false))

    @Test fun newPhone_locationReady_asksForNotifications() =
        assertEquals(PermissionStep.Notifications, step(notificationsNeeded = true))

    @Test fun newPhone_alreadyGranted_isNotAskedAgain() {
        // NotificationChecks.needsPrompt() is false when granted, so "needed" is false.
        assertEquals(PermissionStep.Done, step(notificationsNeeded = false, notificationsAnswered = false))
    }

    @Test fun notifications_answeredEitherWay_isDone() =
        assertEquals(PermissionStep.Done, step(notificationsNeeded = true, notificationsAnswered = true))

    // ---------- Order ----------

    @Test fun location_comesBeforeNotifications() {
        assertEquals(
            PermissionStep.Location(LocationGate.AskPermission),
            step(gate = LocationGate.AskPermission, notificationsNeeded = true)
        )
        assertEquals(
            PermissionStep.Location(LocationGate.GpsOff),
            step(gate = LocationGate.GpsOff, notificationsNeeded = true)
        )
    }

    // ---------- Item 5: continuing always works ----------

    @Test fun skippingLocation_movesToNotifications_thenDone() {
        val denied = LocationGate.PermissionDenied(canAskAgain = false)
        assertEquals(PermissionStep.Notifications, step(gate = denied, locationSkipped = true, notificationsNeeded = true))
        assertEquals(
            PermissionStep.Done,
            step(gate = denied, locationSkipped = true, notificationsNeeded = true, notificationsAnswered = true)
        )
    }

    @Test fun skippingLocation_onOldPhone_isDoneImmediately() =
        assertEquals(PermissionStep.Done, step(gate = LocationGate.GpsOff, locationSkipped = true))

    @Test fun noCombinationOfAnswers_canTrapThePerson() {
        val gates = listOf(
            LocationGate.AskPermission,
            LocationGate.PermissionDenied(true),
            LocationGate.PermissionDenied(false),
            LocationGate.GpsOff,
            LocationGate.Ready
        )
        for (gate in gates) for (needed in listOf(true, false)) {
            // The person taps "Continue without location" and answers the notification prompt.
            val result = step(gate = gate, locationSkipped = true, notificationsNeeded = needed, notificationsAnswered = true)
            assertEquals("gate=$gate needed=$needed", PermissionStep.Done, result)
        }
    }
}
