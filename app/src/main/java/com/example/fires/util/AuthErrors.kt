package com.example.fires.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Turns Firebase exceptions into messages a citizen can act on. All auth wording is here,
 * next to the Validators messages, so the ViewModels stay free of Firebase details.
 */
object AuthErrors {

    const val MSG_BAD_CREDENTIALS = "Incorrect email or password."
    const val MSG_DISABLED = "This account has been disabled. Contact the barangay office."
    const val MSG_NETWORK = "No internet connection. Check your connection and try again."
    const val MSG_TOO_MANY = "Too many attempts. Wait a few minutes and try again."
    const val MSG_EMAIL_TAKEN = "An account with this email already exists. Try logging in instead."
    const val MSG_WEAK_PASSWORD = "Choose a stronger password."
    const val MSG_LOGIN_FALLBACK = "Could not log in. Please try again."
    const val MSG_SIGNUP_FALLBACK = "Could not create your account. Please try again."

    /** Firebase project problem, not the citizen's fault (Email/Password not switched on, etc.). */
    const val MSG_SIGNUP_SETUP =
        "Sign-up is not switched on for this app yet. Please contact the barangay office."

    /** Login succeeded but users/{uid} could not be read, so we signed the person out again. */
    const val MSG_LOAD_PROFILE =
        "We could not load your account. Check your connection and try again."

    /** Sign-up half-finished: the account exists but users/{uid} was not saved. */
    const val MSG_PROFILE_SAVE =
        "Your account was created, but we could not save your details. " +
            "Check your connection and tap Try again."

    /** Sign-up was interrupted by a sign-out, so there is nothing left to retry. */
    const val MSG_SIGNED_OUT =
        "You were signed out. Your account was already created, so go back and log in."

    /** Profile setup could not be saved for a reason that is not the connection. */
    const val MSG_PROFILE_FALLBACK = "Could not save your details. Please try again."

    /** Message for a failed profile-setup save (offline and timeouts say so; the rest is generic). */
    fun forProfileSave(e: Throwable): String = connectionMessage(e) ?: MSG_PROFILE_FALLBACK

    /** Wrong email and wrong password give the same message on purpose (do not reveal which). */
    fun forLogin(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") MSG_DISABLED else MSG_BAD_CREDENTIALS
        is FirebaseAuthInvalidCredentialsException -> MSG_BAD_CREDENTIALS
        else -> connectionMessage(e) ?: MSG_LOGIN_FALLBACK
    }

    /** Where a sign-up failure should be shown. Null fields mean "not this one". */
    data class SignUpFailure(
        val emailError: String? = null,
        val passwordError: String? = null,
        val message: String? = null
    )

    fun forSignUp(e: Throwable): SignUpFailure = when (e) {
        is FirebaseAuthUserCollisionException -> SignUpFailure(emailError = MSG_EMAIL_TAKEN)
        // Weak-password extends invalid-credentials, so it must be checked first.
        is FirebaseAuthWeakPasswordException -> SignUpFailure(passwordError = MSG_WEAK_PASSWORD)
        is FirebaseAuthInvalidCredentialsException ->
            SignUpFailure(emailError = Validators.MSG_EMAIL_INVALID)
        else -> SignUpFailure(message = connectionMessage(e) ?: signUpFallback(e))
    }

    /**
     * Anything not matched above. Setup problems get their own message. Otherwise the error code
     * is added in brackets so a failed sign-up can be diagnosed from the screen alone.
     * TODO: drop the bracket once sign-up is stable.
     */
    private fun signUpFallback(e: Throwable): String {
        val code = (e as? FirebaseAuthException)?.errorCode
        val text = e.message.orEmpty()
        return when {
            code == "ERROR_OPERATION_NOT_ALLOWED" ||
                text.contains("CONFIGURATION_NOT_FOUND") ||
                text.contains("OPERATION_NOT_ALLOWED") -> MSG_SIGNUP_SETUP
            code != null -> "$MSG_SIGNUP_FALLBACK ($code)"
            else -> "$MSG_SIGNUP_FALLBACK (${e.javaClass.simpleName})"
        }
    }

    private fun connectionMessage(e: Throwable): String? = when {
        e is FirebaseNetworkException -> MSG_NETWORK
        e is FirebaseTooManyRequestsException -> MSG_TOO_MANY
        e is TimeoutCancellationException -> MSG_NETWORK
        e is FirebaseFirestoreException &&
            (e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) -> MSG_NETWORK
        else -> null
    }
}
