package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRulesTest {
    @Test fun initials_use_first_and_last_word() {
        assertEquals("MS", ProfileRules.initials("Maria Santos"))
        assertEquals("MC", ProfileRules.initials("  maria  dela  cruz "))
        assertEquals("M", ProfileRules.initials("Maria"))
        assertEquals("?", ProfileRules.initials("   "))
    }

    @Test fun blank_details_show_a_dash() {
        assertEquals("—", ProfileRules.valueOrDash("  "))
        assertEquals("Purok 3", ProfileRules.valueOrDash(" Purok 3 "))
    }
}
