package com.example.fires.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase Authentication only. Roles and profile data live in UserRepository (users/{uid}). */
class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email

    /**
     * Signs in and returns the uid.
     * Throws a FirebaseAuthException subclass for wrong credentials, disabled account, etc.,
     * or FirebaseNetworkException when offline. AuthErrors turns those into screen messages.
     */
    suspend fun signIn(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: throw IllegalStateException("Sign-in returned no user")
    }

    /**
     * Creates the Firebase account and returns the uid. The new user is signed in automatically.
     * This does NOT create users/{uid}; the caller must do that (see SignUpViewModel).
     */
    suspend fun createAccount(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: throw IllegalStateException("Sign-up returned no user")
    }

    fun signOut() = auth.signOut()
}
