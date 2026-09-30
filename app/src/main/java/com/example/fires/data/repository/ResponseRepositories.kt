package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Assignment
import com.example.fires.data.model.IncidentRecord
import com.example.fires.data.observeList
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
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
}

/** Historical incident records: records/{incidentId} (one record per resolved incident). */
class RecordRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val records = db.collection(FireCollections.RECORDS)

    fun observeAll(): Flow<List<IncidentRecord>> =
        records.orderBy("dateResolved", Query.Direction.DESCENDING).observeList(IncidentRecord::class.java)

    /** Using the incident id as the document id makes resolving twice harmless (it overwrites). */
    fun create(record: IncidentRecord): Task<Void> =
        records.document(record.incidentId).set(record)
}
