package com.example.fires.util

/**
 * Form validation rules for the sign-up and login screens (C2).
 *
 * One place for every rule and message, so the screens, the ViewModels and the unit tests
 * all use the same code. Pure Kotlin (no Android classes), so it runs as a plain JVM test.
 *
 * Every check returns null when the value is fine, or the message to show under the field.
 * That is exactly what LabeledTextField(error = ...) expects.
 */
object Validators {

    const val MIN_PASSWORD_LENGTH = 8

    // Messages live here so the UI and the tests never disagree.
    const val MSG_NAME_BLANK = "Enter your full name."
    const val MSG_EMAIL_BLANK = "Enter your email address."
    const val MSG_EMAIL_INVALID = "Enter a valid email address, like name@example.com."
    const val MSG_CONTACT_BLANK = "Enter your contact number."
    const val MSG_CONTACT_INVALID = "Enter a valid mobile number, like 09171234567."
    const val MSG_PASSWORD_BLANK = "Enter your password."
    const val MSG_PASSWORD_SHORT = "Password must be at least $MIN_PASSWORD_LENGTH characters."
    const val MSG_CONFIRM_MISMATCH = "Passwords do not match."
    const val MSG_ADDRESS_BLANK = "Enter your home address."
    const val MSG_EMERGENCY_BLANK = "Enter an emergency contact number."
    const val MSG_EMERGENCY_INVALID = "Enter a valid mobile number, like 09171234567."

    // Simple, readable email shape: something@domain.tld (no spaces, one @, a dot in the domain).
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}$")

    // Philippine mobile numbers: 09XXXXXXXXX, +639XXXXXXXXX or 639XXXXXXXXX.
    private val PH_MOBILE = Regex("^(09|\\+?639)\\d{9}$")

    // ---------- Single-field checks ----------

    fun name(value: String): String? =
        if (value.isBlank()) MSG_NAME_BLANK else null

    /** Blank and bad-format are separate messages. Used by both sign-up and login. */
    fun email(value: String): String? {
        val v = value.trim()
        return when {
            v.isEmpty() -> MSG_EMAIL_BLANK
            !EMAIL.matches(v) -> MSG_EMAIL_INVALID
            else -> null
        }
    }

    /** Spaces and dashes are ignored, so "0917 123 4567" and "0917-123-4567" pass. */
    fun contactNumber(value: String): String? =
        mobileNumber(value, blank = MSG_CONTACT_BLANK, invalid = MSG_CONTACT_INVALID)

    /** Profile setup (C9): where the person lives. Free text, so the only rule is "not blank". */
    fun address(value: String): String? =
        if (value.isBlank()) MSG_ADDRESS_BLANK else null

    /** Profile setup (C9): same phone rule as the contact number, with its own messages. */
    fun emergencyContact(value: String): String? =
        mobileNumber(value, blank = MSG_EMERGENCY_BLANK, invalid = MSG_EMERGENCY_INVALID)

    /** What gets saved: spaces and dashes are not stored, so "0917 123 4567" becomes "09171234567". */
    fun normalizePhone(value: String): String = value.filterNot { it == ' ' || it == '-' }

    private fun mobileNumber(value: String, blank: String, invalid: String): String? {
        val v = normalizePhone(value)
        return when {
            v.isEmpty() -> blank
            !PH_MOBILE.matches(v) -> invalid
            else -> null
        }
    }

    /** Sign-up rule: minimum length. A blank password is too short, so it gets the length message. */
    fun newPassword(value: String): String? =
        if (value.length < MIN_PASSWORD_LENGTH) MSG_PASSWORD_SHORT else null

    fun confirmPassword(password: String, confirm: String): String? =
        if (password != confirm) MSG_CONFIRM_MISMATCH else null

    /**
     * Login rule: only "not blank". The length rule is deliberately NOT applied here, because
     * login must not reject an existing account. Firebase decides if the password is correct.
     */
    fun loginPassword(value: String): String? =
        if (value.isEmpty()) MSG_PASSWORD_BLANK else null

    // ---------- Whole-form checks ----------

    data class SignUpErrors(
        val name: String? = null,
        val email: String? = null,
        val contactNo: String? = null,
        val password: String? = null,
        val confirmPassword: String? = null
    ) {
        val isValid: Boolean
            get() = name == null && email == null && contactNo == null &&
                password == null && confirmPassword == null
    }

    data class LoginErrors(
        val email: String? = null,
        val password: String? = null
    ) {
        val isValid: Boolean get() = email == null && password == null
    }

    fun validateSignUp(
        name: String,
        email: String,
        contactNo: String,
        password: String,
        confirmPassword: String
    ) = SignUpErrors(
        name = name(name),
        email = email(email),
        contactNo = contactNumber(contactNo),
        password = newPassword(password),
        confirmPassword = confirmPassword(password, confirmPassword)
    )

    data class ProfileErrors(
        val name: String? = null,
        val contactNo: String? = null,
        val address: String? = null,
        val emergencyContact: String? = null
    ) {
        val isValid: Boolean
            get() = name == null && contactNo == null && address == null && emergencyContact == null
    }

    /**
     * Profile setup form. Purok has no rule: it is optional and is picked from a dropdown (see
     * data/Puroks.kt, whose list still has to be confirmed with B-FLARE). The contact number is only checked when
     * the screen actually shows that field (requireContact), see ProfileSetupViewModel.
     */
    fun validateProfile(
        name: String,
        contactNo: String,
        address: String,
        emergencyContact: String,
        requireContact: Boolean
    ) = ProfileErrors(
        name = name(name),
        contactNo = if (requireContact) contactNumber(contactNo) else null,
        address = address(address),
        emergencyContact = emergencyContact(emergencyContact)
    )

    fun validateLogin(email: String, password: String) = LoginErrors(
        email = email(email),
        password = loginPassword(password)
    )
}
