package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Role
import com.example.fires.data.model.User
import com.example.fires.data.observeObject
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val users = db.collection(FireCollections.USERS)

    /** Returns null when the user document does not exist yet. Throws on network/permission errors. */
    suspend fun getUser(uid: String): User? =
        users.document(uid).get().await().toObject(User::class.java)

    fun observeUser(uid: String): Flow<User?> = users.document(uid).observeObject(User::class.java)

    /** First write for a new account. Also stamps createdAt. */
    suspend fun createUser(uid: String, user: User) {
        users.document(uid).set(user).await()
    }

    /**
     * Profile setup. Saves the profile whether or not users/{uid} exists yet.
     *
     * Sign-up can leave an account without a document (the document write failed and the person
     * closed the app), and toSessionState() sends those people here. update() would fail on a
     * missing document, so in that case the document is created with role = citizen instead.
     * An existing document goes through updateProfile(), so role and createdAt are never touched.
     */
    suspend fun saveProfile(
        uid: String,
        email: String,
        fullName: String,
        contactNo: String,
        address: String,
        purok: String,
        emergencyContact: String
    ) {
        if (users.document(uid).get().await().exists()) {
            updateProfile(uid, fullName, contactNo, address, purok, emergencyContact)
        } else {
            createUser(
                uid,
                User(
                    fullName = fullName,
                    email = email,
                    contactNo = contactNo,
                    address = address,
                    purok = purok,
                    emergencyContact = emergencyContact,
                    role = Role.CITIZEN.value
                )
            )
        }
    }

    /** Profile edits. Uses update() so createdAt and role are never overwritten. */
    suspend fun updateProfile(
        uid: String,
        fullName: String,
        contactNo: String,
        address: String,
        purok: String,
        emergencyContact: String
    ) {
        users.document(uid).update(
            mapOf(
                "fullName" to fullName,
                "contactNo" to contactNo,
                "address" to address,
                "purok" to purok,
                "emergencyContact" to emergencyContact,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
