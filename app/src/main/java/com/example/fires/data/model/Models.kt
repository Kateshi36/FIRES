package com.example.fires.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/*
 * Firestore models (from the ERD).
 *
 * Rules for Firestore data classes:
 *  - every property has a default value (Firestore needs a no-argument constructor)
 *  - properties are `var`
 *  - do NOT add computed properties or methods starting with "get"/"is" inside these
 *    classes, Firestore would try to save them. Helpers live below as extension functions.
 */

data class User(
    @DocumentId var id: String = "",
    var fullName: String = "",
    var email: String = "",
    var contactNo: String = "",
    var address: String = "",
    var purok: String = "",
    var emergencyContact: String = "",
    var role: String = Role.CITIZEN.value,
    @ServerTimestamp var createdAt: Timestamp? = null
)

data class Incident(
    @DocumentId var id: String = "",
    var reporterId: String = "",
    // Copied from the reporter's profile so responders don't need an extra read.
    var reporterName: String = "",
    var reporterContact: String = "",

    var fireType: String = FireType.OTHER.value,
    var description: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var locationSource: String = LocationSource.GPS.value,
    var addressText: String = "",

    var fireSize: String = FireSize.SMALL.value,
    var peopleAtRisk: Int = 0,
    var vulnerablePersons: List<String> = emptyList(),
    var trapped: Boolean = false,
    var hazards: List<String> = emptyList(), // HazardType values, e.g. "lpg_tank"
    var hasPhoto: Boolean = false,

    var status: String = IncidentStatus.REPORTED.value,
    var verification: String = Verification.PENDING.value,
    var severity: String = Severity.LOW.value,
    var severitySource: String = SeveritySource.AUTO.value,

    // Kotlin turns "isDuplicate" into the Firestore field "duplicate" unless we pin the name.
    @get:PropertyName("isDuplicate") @set:PropertyName("isDuplicate")
    var isDuplicate: Boolean = false,
    var duplicateOf: String? = null,
    @get:PropertyName("isSuspicious") @set:PropertyName("isSuspicious")
    var isSuspicious: Boolean = false,

    @ServerTimestamp var submittedAt: Timestamp? = null,
    @ServerTimestamp var updatedAt: Timestamp? = null
)

/** Photo stored as compressed Base64. Document id == incident id. */
data class IncidentPhoto(
    @DocumentId var id: String = "",
    var base64: String = "",
    var mimeType: String = "image/jpeg",
    var sizeBytes: Long = 0
)

/** incidents/{incidentId}/messages/{id} */
data class Message(
    @DocumentId var id: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var senderRole: String = Role.CITIZEN.value,
    var messageText: String = "",
    @ServerTimestamp var sentAt: Timestamp? = null
)

/** incidents/{incidentId}/assignments/{id} */
data class Assignment(
    @DocumentId var id: String = "",
    var responderId: String = "",
    var responderName: String = "",
    var unit: String = "",                       // e.g. "B-FLARE", "BDRRMO", "BFP"
    var assignedResources: List<String> = emptyList(), // e.g. "Fire truck", "Water tanker"
    @ServerTimestamp var assignedAt: Timestamp? = null
)

/** records/{incidentId}: one record per resolved incident (ERD: incident 1 to 0..1 record). */
data class IncidentRecord(
    @DocumentId var id: String = "",
    var incidentId: String = "",
    var resolvedBy: String = "",
    var remarks: String = "",
    @ServerTimestamp var dateResolved: Timestamp? = null
)

// ---- Helpers (extension functions are NOT saved to Firestore) ----

fun User.roleEnum(): Role = Role.fromValue(role)
fun User.hasCompleteProfile(): Boolean = fullName.isNotBlank() && address.isNotBlank()

fun Incident.fireTypeEnum(): FireType = FireType.fromValue(fireType)
fun Incident.fireSizeEnum(): FireSize = FireSize.fromValue(fireSize)
fun Incident.statusEnum(): IncidentStatus = IncidentStatus.fromValue(status)
fun Incident.verificationEnum(): Verification = Verification.fromValue(verification)
fun Incident.severityEnum(): Severity = Severity.fromValue(severity)
