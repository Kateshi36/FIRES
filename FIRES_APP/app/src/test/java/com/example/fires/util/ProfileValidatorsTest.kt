package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C9: the profile setup rules (address, emergency contact, whole-form check). */
class ProfileValidatorsTest {

    // ---------- Address ----------

    @Test fun address_blank_isRejected() {
        assertEquals(Validators.MSG_ADDRESS_BLANK, Validators.address(""))
        assertEquals(Validators.MSG_ADDRESS_BLANK, Validators.address("   "))
    }

    @Test fun address_filled_isAccepted() = assertNull(Validators.address("123 Rizal St."))

    // ---------- Emergency contact ----------

    @Test fun emergency_blank_hasItsOwnMessage() =
        assertEquals(Validators.MSG_EMERGENCY_BLANK, Validators.emergencyContact(" "))

    @Test fun emergency_badNumbers_areRejected() {
        listOf("12345", "0817123456", "091712345678", "09abc123456")
            .forEach { assertEquals("'$it'", Validators.MSG_EMERGENCY_INVALID, Validators.emergencyContact(it)) }
    }

    @Test fun emergency_goodNumbers_areAccepted() {
        listOf("09171234567", "+639171234567", "0917 123 4567", "0917-123-4567")
            .forEach { assertNull("'$it'", Validators.emergencyContact(it)) }
    }

    // ---------- Normalizing ----------

    @Test fun normalizePhone_dropsSpacesAndDashes() {
        assertEquals("09171234567", Validators.normalizePhone("0917 123-4567"))
        assertEquals("+639171234567", Validators.normalizePhone("+63 917 123 4567"))
    }

    // ---------- Whole form ----------

    @Test fun profile_emptyForm_flagsRequiredFields() {
        val e = Validators.validateProfile("", "", "", "", requireContact = false)
        assertEquals(Validators.MSG_NAME_BLANK, e.name)
        assertEquals(Validators.MSG_ADDRESS_BLANK, e.address)
        assertEquals(Validators.MSG_EMERGENCY_BLANK, e.emergencyContact)
        assertFalse(e.isValid)
    }

    @Test fun profile_contactNumber_isSkippedWhenFieldIsHidden() {
        val e = Validators.validateProfile("Juan", "", "Purok 3", "09171234567", requireContact = false)
        assertNull(e.contactNo)
        assertTrue(e.isValid)
    }

    @Test fun profile_contactNumber_isCheckedWhenFieldIsShown() {
        val e = Validators.validateProfile("Juan", "", "Purok 3", "09171234567", requireContact = true)
        assertEquals(Validators.MSG_CONTACT_BLANK, e.contactNo)
        assertFalse(e.isValid)
    }

    @Test fun profile_completeForm_isValid() {
        val e = Validators.validateProfile("Juan", "09171234567", "Purok 3", "09181234567", requireContact = true)
        assertTrue(e.isValid)
    }
}
