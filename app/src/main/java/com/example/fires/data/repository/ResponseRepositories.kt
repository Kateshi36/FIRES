package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Assignment
import com.example.fires.data.model.IncidentRecord
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.observeList
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow

/** Who/what is assigned to an incident: incidents/{incidentId}/assignments */
class AssignmentRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun col(incidentId: String) =
        db.collection(FireCollections.INCIDENTS).document(incidentId).collection(FireCollections.ASSIGNMENTS)

    fun observe(incidentId: String): Flow<List<Assignment>> =
        col(incidentId).orderBy("assignedAt", Query.Direction.ASCENDING).observeList(Assignment::class.java)

    fun assign(incidentId: String, assignment: Assignment): Task<DocumentReference> =
        col(incidentId).add(assignment)

    /** A new assignment id, made up front so a retry can write the same document again. */
    fun newId(incidentId: String): String = col(incidentId).document().id

    /**
     * Writes the assignment under a known id. Saving twice with the same id overwrites instead of
     * adding a second assignment, so "tap Assign again" after a timeout is harmless. Returns the
     * Task (not awaited) like the other write functions: it only completes when the server confirms.
     */
    fun save(incidentId: String, id: String, assignment: Assignment): Task<Void> =
        col(incidentId).document(id).set(assignment)
}

/** Historical incident records: records/{incidentId} (one record per resolved incident). */
class RecordRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val records = db.collection(FireCollections.RECORDS)

    fun observeAll(): Flow<List<IncidentRecord>> =
        records.orderBy("dateResolved", Query.Direction.DESCENDING).observeList(IncidentRecord::class.java)

    /** Using the incident id as the document id makes resolving twice harmless (it overwrites). */
    fun create(record: IncidentRecord): Task<Void> =
        records.document(record.incidentId).set(record)

    /**
     * Resolves an incident (E5): creates records/{incidentId} AND sets the incident to RESOLVED in
     * one batch, so both happen or neither does. A report can never be resolved without its
     * record, or have a record while still open. Like the other writes, the Task only completes
     * when the server confirms. Running it again with the same record just overwrites it.
     * (update() does not apply @ServerTimestamp, so updatedAt is set here.)
     */
    fun resolve(record: IncidentRecord): Task<Void> {
        val batch = db.batch()
        batch.set(records.document(record.incidentId), record)
        batch.update(
            db.collection(FireCollections.INCIDENTS).document(record.incidentId),
            mapOf(
                "status" to IncidentStatus.RESOLVED.value,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        return batch.commit()
    }
}
