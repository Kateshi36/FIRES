package com.example.fires.data

import com.example.fires.data.model.DeliveryState
import com.example.fires.data.model.deliveryStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryStateTest {

    @Test fun savedOnPhoneAndWaiting_isSending() {
        // Offline, or the server has not answered yet.
        assertEquals(DeliveryState.SENDING, deliveryStateOf(exists = true, hasPendingWrites = true, fromCache = true))
        assertEquals(DeliveryState.SENDING, deliveryStateOf(exists = true, hasPendingWrites = true, fromCache = false))
    }

    @Test fun nothingPending_isSent() {
        assertEquals(DeliveryState.SENT, deliveryStateOf(exists = true, hasPendingWrites = false, fromCache = false))
        // Confirmed earlier, now read from the phone's copy while offline: still sent.
        assertEquals(DeliveryState.SENT, deliveryStateOf(exists = true, hasPendingWrites = false, fromCache = true))
    }

    @Test fun missingAfterTheServerAnswered_isFailed() =
        assertEquals(DeliveryState.FAILED, deliveryStateOf(exists = false, hasPendingWrites = false, fromCache = false))

    @Test fun missingButOnlyFromCache_isUnknown() =
        assertNull(deliveryStateOf(exists = false, hasPendingWrites = false, fromCache = true))
}
