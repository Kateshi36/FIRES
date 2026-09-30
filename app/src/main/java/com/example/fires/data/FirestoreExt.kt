package com.example.fires.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Collection names in one place so a typo can't create a second collection. */
object FireCollections {
    const val USERS = "users"
    const val INCIDENTS = "incidents"
    const val PHOTOS = "incident_photos"
    const val MESSAGES = "messages"        // subcollection of incidents
    const val ASSIGNMENTS = "assignments"  // subcollection of incidents
    const val RECORDS = "records"
}

/** Live list of documents. Emits again on every change, stops when the collector is cancelled. */
fun <T : Any> Query.observeList(clazz: Class<T>): Flow<List<T>> = callbackFlow<List<T>> {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) trySend(snapshot.toObjects(clazz))
    }
    awaitClose { registration.remove() }
}

/** Live single document. Emits null when the document does not exist. */
fun <T : Any> DocumentReference.observeObject(clazz: Class<T>): Flow<T?> = callbackFlow<T?> {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(clazz))
    }
    awaitClose { registration.remove() }
}
