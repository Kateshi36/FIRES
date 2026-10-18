package com.example.fires.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.MetadataChanges
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
    const val RESPONDER_LOCATIONS = "responderLocations" // subcollection of incidents
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

/** A list plus where it came from. [fromCache]: built only from the copy saved on the phone, so it may be old. */
data class ListSnapshot<T>(val items: List<T>, val fromCache: Boolean)

/**
 * Like [observeList], but each emission says whether it came from the phone's saved copy or from
 * the server. Needs MetadataChanges.INCLUDE: with the default, Firestore does NOT raise an event
 * when the saved copy is confirmed by the server without any change, and a listener that waits
 * for "the first server snapshot" would wait forever.
 */
fun <T : Any> Query.observeListWithSource(clazz: Class<T>): Flow<ListSnapshot<T>> = callbackFlow<ListSnapshot<T>> {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        if (snapshot != null) {
            trySend(ListSnapshot(snapshot.toObjects(clazz), snapshot.metadata.isFromCache))
        }
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
