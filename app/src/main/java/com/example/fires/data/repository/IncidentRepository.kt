package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentDelivery
import com.example.fires.data.model.IncidentPhoto
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.example.fires.data.model.SeveritySource
import com.example.fires.data.model.Verification
import com.example.fires.data.model.deliveryStateOf
import com.example.fires.data.ListSnapshot
import com.example.fires.data.observeList
import com.example.fires.data.observeListWithSource
import com.example.fires.data.observeObject
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

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

    /**
     * Responder dashboard: only incidents that are still open (reported, verified, dispatched, on
     * scene), so the listener does not grow with every report ever sent. One field filter and no
     * orderBy, so Firestore needs no composite index. The dashboard sorts the result itself.
     */
    fun observeActive(): Flow<List<Incident>> = activeQuery().observeList(Incident::class.java)

    private fun activeQuery(): Query =
        incidents.whereIn("status", IncidentStatus.entries.filter { it.isActive }.map { it.value })

    /**
     * The same query as [observeActive], but each emission also says where the data came from, for
     * the responder alert service (F1.6). Needs MetadataChanges.INCLUDE: with the default,
     * Firestore does NOT raise an event when the saved copy on the phone is confirmed by the
     * server without any change, and the service would wait for that confirmation forever.
     */
    fun observeActiveSnapshots(): Flow<ActiveSnapshot> = callbackFlow<ActiveSnapshot> {
        val registration = activeQuery()
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    ActiveSnapshot(
                        incidents = snapshot.toObjects(Incident::class.java),
                        fromCache = snapshot.metadata.isFromCache,
                        pendingIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    /**
     * Incident history (E7): only closed reports (resolved or dismissed). One field filter and no
     * orderBy, so Firestore needs no composite index. The history screen sorts and filters itself.
     */
    fun observeClosed(): Flow<List<Incident>> =
        incidents.whereIn("status", IncidentStatus.entries.filter { !it.isActive }.map { it.value })
            .observeList(Incident::class.java)

    /**
     * Reports grouped under [primaryId] as duplicates (E2). One field filter and no orderBy, so
     * Firestore needs no composite index. The detail screen sorts the result itself.
     */
    fun observeDuplicatesOf(primaryId: String): Flow<List<Incident>> =
        incidents.whereEqualTo("duplicateOf", primaryId).observeList(Incident::class.java)

    /** Citizen view: only my incidents. Sorted here to avoid needing a Firestore composite index. */
    fun observeMine(uid: String): Flow<List<Incident>> =
        incidents.whereEqualTo("reporterId", uid).observeList(Incident::class.java).map { list ->
            // A just-created report has no server time yet (null): treat it as newest.
            list.sortedByDescending { it.submittedAt?.seconds ?: Long.MAX_VALUE }
        }

    /**
     * My incidents, with where each emission came from, for the citizen's local notifications
     * (F2). Same query as [observeMine]; the caller needs no sorting.
     */
    fun observeMineSnapshots(uid: String): Flow<ListSnapshot<Incident>> =
        incidents.whereEqualTo("reporterId", uid).observeListWithSource(Incident::class.java)

    /**
     * Like [observe], but also says whether the server has the report yet (D6). Emits again when
     * that changes: SENDING while the write is only queued on the phone (offline), SENT once the
     * server confirms, FAILED if the server refuses it. Needs MetadataChanges.INCLUDE, otherwise
     * Firestore would not report the "pending -> confirmed" change.
     */
    fun observeDelivery(id: String): Flow<IncidentDelivery> = callbackFlow<IncidentDelivery> {
        val registration = incidents.document(id)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val state = deliveryStateOf(
                    exists = snapshot.exists(),
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    fromCache = snapshot.metadata.isFromCache
                ) ?: return@addSnapshotListener // cannot tell yet, wait for the next update
                trySend(IncidentDelivery(snapshot.toObject(Incident::class.java), state))
            }
        awaitClose { registration.remove() }
    }

    // ---- Checks before saving (D5) ----
    // One-time reads, not listeners. Both are plain queries on one field, so Firestore needs no
    // composite index. They are slow or fail on a weak connection, so the caller puts a time
    // limit on them and sends the report anyway if they do not answer.

    /** Reports sent after [sinceMillis], from anyone. Used to spot a duplicate of a nearby fire. */
    suspend fun recentIncidents(sinceMillis: Long): List<Incident> =
        incidents.whereGreaterThan("submittedAt", Timestamp(Date(sinceMillis)))
            .get().await().toObjects(Incident::class.java)

    /** Every report this account has sent. Used to spot an account that spams or sends false reports. */
    suspend fun myIncidents(uid: String): List<Incident> =
        incidents.whereEqualTo("reporterId", uid)
            .get().await().toObjects(Incident::class.java)

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

/**
 * One emission of [IncidentRepository.observeActiveSnapshots].
 * [fromCache]: built only from the copy saved on the phone, so it may be old.
 * [pendingIds]: the incidents that have a change made on THIS phone which the server has not
 * confirmed yet, so the responder's own taps can be told apart from other people's changes (F1.12).
 */
data class ActiveSnapshot(
    val incidents: List<Incident>,
    val fromCache: Boolean,
    val pendingIds: Set<String>
)
