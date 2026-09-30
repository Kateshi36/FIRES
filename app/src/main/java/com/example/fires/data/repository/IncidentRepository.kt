package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentPhoto
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.SeveritySource
import com.example.fires.data.model.Verification
import com.example.fires.data.observeList
import com.example.fires.data.observeObject
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class IncidentRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val incidents = db.collection(FireCollections.INCIDENTS)
    private val photos = db.collection(FireCollections.PHOTOS)

    // ---- Create (citizen) ----
    // save()/savePhoto() return the Task instead of awaiting it on purpose: a Firestore write Task
    // only completes when the SERVER confirms, so awaiting it would freeze the UI while offline.
    // The data is queued locally and syncs later. Phase D shows "sending / sent" using this.

    fun newId(): String = incidents.document().id

    fun save(id: String, incident: Incident): Task<Void> = incidents.document(id).set(incident)

    fun savePhoto(incidentId: String, photo: IncidentPhoto): Task<Void> =
        photos.document(incidentId).set(photo)

    // ---- Read ----

    fun observe(id: String): Flow<Incident?> = incidents.document(id).observeObject(Incident::class.java)

    /** Responder view: every incident, newest first. */
    fun observeAll(): Flow<List<Incident>> =
        incidents.orderBy("submittedAt", Query.Direction.DESCENDING).observeList(Incident::class.java)

    /** Citizen view: only my incidents. Sorted here to avoid needing a Firestore composite index. */
    fun observeMine(uid: String): Flow<List<Incident>> =
        incidents.whereEqualTo("reporterId", uid).observeList(Incident::class.java).map { list ->
            // A just-created report has no server time yet (null): treat it as newest.
            list.sortedByDescending { it.submittedAt?.seconds ?: Long.MAX_VALUE }
        }

    suspend fun getPhoto(incidentId: String): IncidentPhoto? =
        photos.document(incidentId).get().await().toObject(IncidentPhoto::class.java)

    // ---- Responder actions ----
    // update() does not apply @ServerTimestamp, so we set updatedAt ourselves every time.

    fun updateStatus(id: String, status: IncidentStatus): Task<Void> =
        incidents.document(id).update(
            mapOf("status" to status.value, "updatedAt" to FieldValue.serverTimestamp())
        )

    /**
     * Records the responder's decision. Verified moves a report to VERIFIED; false/duplicate
     * moves it to DISMISSED. (The responder UI should only offer this while status == REPORTED.)
     */
    fun setVerification(id: String, verification: Verification): Task<Void> {
        val updates = mutableMapOf<String, Any>(
            "verification" to verification.value,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        when (verification) {
            Verification.VERIFIED -> updates["status"] = IncidentStatus.VERIFIED.value
            Verification.FALSE, Verification.DUPLICATE -> updates["status"] = IncidentStatus.DISMISSED.value
            Verification.PENDING -> Unit
        }
        return incidents.document(id).update(updates)
    }

    fun overrideSeverity(id: String, severity: Severity): Task<Void> =
        incidents.document(id).update(
            mapOf(
                "severity" to severity.value,
                "severitySource" to SeveritySource.RESPONDER.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
}
