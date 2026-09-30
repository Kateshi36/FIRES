package com.example.fires.data.model

/*
 * Every enum is stored in Firestore as its plain string `value`
 * (e.g. status = "on_scene"), so the website can read the same data later.
 */

enum class Role(val value: String) {
    CITIZEN("citizen"), RESPONDER("responder"), ADMIN("admin");

    companion object {
        // Unknown role falls back to the least-privileged one.
        fun fromValue(v: String?): Role = entries.firstOrNull { it.value == v } ?: CITIZEN
    }
}

enum class FireType(val value: String, val label: String) {
    STRUCTURAL("structural", "Structural / house fire"),
    ELECTRICAL("electrical", "Electrical fire"),
    RUBBISH("rubbish", "Rubbish fire"),
    VEHICLE("vehicle", "Vehicle fire"),
    OTHER("other", "Other");

    companion object {
        fun fromValue(v: String?): FireType = entries.firstOrNull { it.value == v } ?: OTHER
    }
}

enum class FireSize(val value: String, val label: String) {
    SMALL("small", "Small (contained)"),
    MEDIUM("medium", "Medium (spreading)"),
    LARGE("large", "Large (out of control)");

    companion object {
        fun fromValue(v: String?): FireSize = entries.firstOrNull { it.value == v } ?: SMALL
    }
}

enum class VulnerableGroup(val value: String, val label: String) {
    CHILDREN("children", "Children"),
    ELDERLY("elderly", "Elderly"),
    PREGNANT("pregnant", "Pregnant"),
    PWD("pwd", "Person with disability");

    companion object {
        fun fromValue(v: String?): VulnerableGroup? = entries.firstOrNull { it.value == v }
    }
}

enum class Severity(val value: String, val label: String, val rank: Int) {
    LOW("low", "Low", 0),
    MEDIUM("medium", "Medium", 1),
    HIGH("high", "High", 2),
    CRITICAL("critical", "Critical", 3);

    companion object {
        fun fromValue(v: String?): Severity = entries.firstOrNull { it.value == v } ?: LOW
    }
}

enum class SeveritySource(val value: String) {
    AUTO("auto"), RESPONDER("responder");

    companion object {
        fun fromValue(v: String?): SeveritySource = entries.firstOrNull { it.value == v } ?: AUTO
    }
}

/** Incident lifecycle. DISPATCHED + ON_SCENE together are the "ongoing" stage in the paper. */
enum class IncidentStatus(val value: String, val label: String) {
    REPORTED("reported", "Reported"),
    VERIFIED("verified", "Verified"),
    DISPATCHED("dispatched", "Dispatched"),
    ON_SCENE("on_scene", "On scene"),
    RESOLVED("resolved", "Resolved"),
    DISMISSED("dismissed", "Dismissed");

    val isActive: Boolean get() = this != RESOLVED && this != DISMISSED

    companion object {
        fun fromValue(v: String?): IncidentStatus = entries.firstOrNull { it.value == v } ?: REPORTED
    }
}

/** Responder's decision about whether the report is real. Separate from the status. */
enum class Verification(val value: String, val label: String) {
    PENDING("pending", "Pending"),
    VERIFIED("verified", "Verified"),
    FALSE("false", "False report"),
    DUPLICATE("duplicate", "Duplicate");

    companion object {
        fun fromValue(v: String?): Verification = entries.firstOrNull { it.value == v } ?: PENDING
    }
}

enum class LocationSource(val value: String) {
    GPS("gps"), PIN("pin");

    companion object {
        fun fromValue(v: String?): LocationSource = entries.firstOrNull { it.value == v } ?: GPS
    }
}
