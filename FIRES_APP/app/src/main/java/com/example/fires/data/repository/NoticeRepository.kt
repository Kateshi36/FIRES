package com.example.fires.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One "assigned" notice written by the administrator's website. */
data class AssignedNotice(val id: String, val incidentId: String, val text: String?, val createdAtMs: Long?, val removed: Boolean = false)

/** users/{uid}/notifications: read-only for the phone (only an administrator creates them). */
class NoticeRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    /** The newest notices of this account (newest first), kept live. Feeds the dashboard bell and banner. */
    fun observeRecent(uid: String, limit: Long = 20): Flow<List<AssignedNotice>> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    val type = d.getString("type")
                    if (type != "assigned" && type != "removed") return@mapNotNull null
                    AssignedNotice(
                        id = d.id,
                        incidentId = d.getString("incidentId") ?: return@mapNotNull null,
                        text = d.getString("text"),
                        createdAtMs = d.getTimestamp("createdAt")?.toDate()?.time,
                        removed = type == "removed"
                    )
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** Emits each newly ADDED notice (and the recent ones when the listener first attaches). */
    fun observeAssigned(uid: String): Flow<AssignedNotice> = callbackFlow {
        val reg = db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                snap?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { c ->
                        val d = c.document
                        val type = d.getString("type")
                        if (type == "assigned" || type == "removed") {
                            trySend(
                                AssignedNotice(
                                    id = d.id,
                                    incidentId = d.getString("incidentId") ?: return@forEach,
                                    text = d.getString("text"),
                                    createdAtMs = d.getTimestamp("createdAt")?.toDate()?.time,
                                    removed = type == "removed"
                                )
                            )
                        }
                    }
            }
        awaitClose { reg.remove() }
    }
}
