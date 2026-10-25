package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotlinePrivacyTest {

    @Test
    fun dialUriUsesTheBarangayRescueNumber() {
        assertEquals("tel:09198947219", Hotline.dialUri())
        assertEquals("09198947219", Hotline.DISPLAY_NUMBER.replace("-", ""))
        assertTrue(Hotline.NO_SIGNAL_TEXT.contains(Hotline.DISPLAY_NUMBER))
        assertTrue(Hotline.REFUSED_TEXT.contains(Hotline.DISPLAY_NUMBER))
        assertEquals("Call Barangay Rescue", Hotline.BUTTON)
    }

    @Test
    fun privacyNoticeCoversWhatIsCollectedWhoSeesItAndTheRights() {
        val all = PrivacyNotice.PARAGRAPHS.joinToString(" ")
        assertTrue(all.contains("GPS location"))
        assertTrue(all.contains("photo"))
        assertTrue(all.contains("administrators"))
        assertTrue(all.contains("AI"))
        assertTrue(all.contains("RA 10173"))
        assertTrue(PrivacyNotice.MSG_CONSENT_REQUIRED.isNotBlank())
    }
}
