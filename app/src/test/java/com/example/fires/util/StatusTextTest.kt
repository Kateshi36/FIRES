package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextTest {

    @Test fun everyStatus_hasItsOwnHeadlineAndDetail() {
        val statuses = IncidentStatus.entries
        assertEquals(statuses.size, statuses.map { statusHeadline(it) }.toSet().size)
        assertEquals(statuses.size, statuses.map { statusDetail(it) }.toSet().size)
        assertTrue(statuses.all { statusHeadline(it).isNotBlank() && statusDetail(it).isNotBlank() })
    }

    @Test fun dismissedWording_doesNotAccuseTheCitizen() {
        val text = (statusHeadline(IncidentStatus.DISMISSED) + statusDetail(IncidentStatus.DISMISSED)).lowercase()
        assertFalse("false" in text)
        assertFalse("fake" in text)
        assertTrue("911" in text) // still tells them what to do if the fire is real
    }
}
