package com.example.fires.data.repository

import com.example.fires.data.FireCollections
import com.example.fires.data.model.Message
import com.example.fires.data.observeList
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow

/** Chat thread for one incident: incidents/{incidentId}/messages */
class MessageRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun thread(incidentId: String): CollectionReference =
        db.collection(FireCollections.INCIDENTS).document(incidentId).collection(FireCollections.MESSAGES)

    fun observe(incidentId: String): Flow<List<Message>> =
        thread(incidentId).orderBy("sentAt", Query.Direction.ASCENDING).observeList(Message::class.java)

    /** Returns the Task (not awaited) so the chat stays usable offline; the message syncs later. */
    fun send(incidentId: String, message: Message): Task<DocumentReference> =
        thread(incidentId).add(message)
}
