package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.ResponderLocation
import com.example.fires.data.observeList
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow

/**
 * Where one responder is right now: incidents/{incidentId}/responderLocations/{responderId}.
 * One document per responder, overwritten on every update (see ResponderLocation and the rules).
 */
class ResponderLocationRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun col(incidentId: String) =
        db.collection(FireCollections.INCIDENTS).document(incidentId)
            .collection(FireCollections.RESPONDER_LOCATIONS)

    /**
     * Every responder currently sharing a location for this incident, live (H4). Emits again on
     * each update, and when a responder stops (their document is deleted). The citizen's rules let
     * the person who reported the incident read this; anyone else gets an error from Firestore.
     */
    fun observe(incidentId: String): Flow<List<ResponderLocation>> =
        col(incidentId).observeList(ResponderLocation::class.java)

    private fun doc(incidentId: String, responderId: String) =
        db.collection(FireCollections.INCIDENTS).document(incidentId)
            .collection(FireCollections.RESPONDER_LOCATIONS).document(responderId)

    /**
     * Writes the current position together with the latest ETA and road distance (H3), in ONE
     * write: the rules need updatedAt to be the server's time on every write, so the ETA cannot
     * be sent on its own. Null ETA or distance means "no route yet". Heading and speed are null
     * when unknown, so an old value never lingers. Not awaited: a Firestore write only completes
     * when the server answers, and the position is replaced seconds later anyway.
     */
    fun update(
        incidentId: String,
        responderId: String,
        latitude: Double,
        longitude: Double,
        heading: Double?,
        speed: Double?,
        etaSeconds: Int? = null,
        distanceMeters: Int? = null
    ): Task<Void> = doc(incidentId, responderId).set(
        mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "heading" to heading,
            "speed" to speed,
            // Kept inside the limits the Firestore rules accept (24 hours, 1,000 km).
            "etaSeconds" to etaSeconds?.coerceIn(0, 86_400),
            "distanceMeters" to distanceMeters?.coerceIn(0, 1_000_000),
            "updatedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.merge()
    )

    /** Stops showing this responder on the citizen's map. Deleting a missing document is harmless. */
    fun remove(incidentId: String, responderId: String): Task<Void> =
        doc(incidentId, responderId).delete()
}
