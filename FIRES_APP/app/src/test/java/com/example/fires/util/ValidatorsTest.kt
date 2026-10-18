package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    // ---------- Name ----------

    @Test fun name_blank_isRejected() {
        assertEquals(Validators.MSG_NAME_BLANK, Validators.name(""))
        assertEquals(Validators.MSG_NAME_BLANK, Validators.name("   "))
    }

    @Test fun name_filled_isAccepted() = assertNull(Validators.name("Juan Dela Cruz"))

    // ---------- Email ----------

    @Test fun email_blank_hasItsOwnMessage() {
        assertEquals(Validators.MSG_EMAIL_BLANK, Validators.email(""))
        assertEquals(Validators.MSG_EMAIL_BLANK, Validators.email("   "))
    }

    @Test fun email_badFormats_areRejected() {
        listOf("juan", "juan@", "@mail.com", "juan@mail", "juan@@mail.com", "ju an@mail.com", "juan@mail.c")
            .forEach { assertEquals("'$it'", Validators.MSG_EMAIL_INVALID, Validators.email(it)) }
    }

    @Test fun email_goodFormats_areAccepted() {
        listOf("juan@mail.com", "juan.dc+fires@mail.co.ph", "  juan@mail.com  ")
            .forEach { assertNull("'$it'", Validators.email(it)) }
    }

    // ---------- Contact number ----------

    @Test fun contact_blank_hasItsOwnMessage() =
        assertEquals(Validators.MSG_CONTACT_BLANK, Validators.contactNumber(" "))

    @Test fun contact_badNumbers_areRejected() {
        listOf("12345", "0817123456", "091712345678", "0917123456", "09abc123456", "+6391712345")
            .forEach { assertEquals("'$it'", Validators.MSG_CONTACT_INVALID, Validators.contactNumber(it)) }
    }

    @Test fun contact_goodNumbers_areAccepted() {
        listOf("09171234567", "+639171234567", "639171234567", "0917 123 4567", "0917-123-4567")
            .forEach { assertNull("'$it'", Validators.contactNumber(it)) }
    }

    // ---------- Password (sign-up) ----------

    @Test fun newPassword_tooShort_isRejected() {
        assertEquals(Validators.MSG_PASSWORD_SHORT, Validators.newPassword(""))
        assertEquals(Validators.MSG_PASSWORD_SHORT, Validators.newPassword("1234567"))
    }

    @Test fun newPassword_minimumLength_isAccepted() {
        assertNull(Validators.newPassword("12345678"))
        assertNull(Validators.newPassword("a much longer passphrase"))
    }

    @Test fun confirmPassword_mismatch_isRejected() {
        assertEquals(Validators.MSG_CONFIRM_MISMATCH, Validators.confirmPassword("secret123", "secret124"))
        assertEquals(Validators.MSG_CONFIRM_MISMATCH, Validators.confirmPassword("secret123", ""))
    }

    @Test fun confirmPassword_match_isAccepted() =
        assertNull(Validators.confirmPassword("secret123", "secret123"))

    // ---------- Whole sign-up form ----------

    @Test fun signUp_emptyForm_givesEachFieldItsOwnMessage() {
        val e = Validators.validateSignUp("", "", "", "", "")
        assertEquals(Validators.MSG_NAME_BLANK, e.name)
        assertEquals(Validators.MSG_EMAIL_BLANK, e.email)
        assertEquals(Validators.MSG_CONTACT_BLANK, e.contactNo)
        assertEquals(Validators.MSG_PASSWORD_SHORT, e.password)
        assertNull(e.confirmPassword) // both blank: they match, the password error already covers it
        assertFalse(e.isValid)
    }

    @Test fun signUp_validForm_hasNoErrors() {
        val e = Validators.validateSignUp("Juan Dela Cruz", "juan@mail.com", "09171234567", "secret123", "secret123")
        assertEquals(Validators.SignUpErrors(), e)
        assertTrue(e.isValid)
    }

    @Test fun signUp_onlyTheBadFieldIsFlagged() {
        val e = Validators.validateSignUp("Juan", "juan@mail.com", "09171234567", "secret123", "different")
        assertNull(e.name); assertNull(e.email); assertNull(e.contactNo); assertNull(e.password)
        assertEquals(Validators.MSG_CONFIRM_MISMATCH, e.confirmPassword)
        assertFalse(e.isValid)
    }

    // ---------- Login ----------

    @Test fun login_blankEmail_andBlankPassword_areSeparateMessages() {
        val e = Validators.validateLogin("", "")
        assertEquals(Validators.MSG_EMAIL_BLANK, e.email)
        assertEquals(Validators.MSG_PASSWORD_BLANK, e.password)
        assertFalse(e.isValid)
    }

    @Test fun login_badEmailFormat_isRejected() {
        val e = Validators.validateLogin("juan@", "secret123")
        assertEquals(Validators.MSG_EMAIL_INVALID, e.email)
        assertNull(e.password)
    }

    @Test fun login_shortPassword_isNotRejectedByTheLengthRule() {
        // Login must leave "wrong password" to Firebase, not reject old/short passwords here.
        val e = Validators.validateLogin("juan@mail.com", "abc")
        assertTrue(e.isValid)
    }

    @Test fun login_validForm_hasNoErrors() =
        assertTrue(Validators.validateLogin("juan@mail.com", "secret123").isValid)
}
