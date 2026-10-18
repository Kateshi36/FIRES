package com.example.fires.util

import com.example.fires.data.model.Assignment

/**
 * The units and resources a responder can assign (E4), and the checks around them. The lists are
 * open items in the project plan (to confirm with B-FLARE / BDRRMO), so they are defined here and
 * nowhere else. They are stored on the assignment as these exact strings.
 */
object AssignmentRules {

    val UNITS = listOf("B-FLARE", "BDRRMO", "BFP", "Neighboring barangay")
    val RESOURCES = listOf("Fire truck", "Water tanker")

    const val MSG_UNIT_REQUIRED = "Choose which unit is responding."
    const val MSG_CLOSED = "This report is closed, so nobody can be assigned to it."
    const val MSG_SIGNED_OUT = "You were signed out. Log in again to assign responders."
    const val MSG_NOT_CONFIRMED =
        "We couldn't confirm the assignment. Check your connection and tap Assign again. " +
            "Tapping again will not create a second assignment."
    const val NO_RESOURCES = "No resources listed"

    /** A unit is required. Resources are optional (volunteers may bring none). */
    fun validateUnit(unit: String?): String? =
        if (unit == null || unit !in UNITS) MSG_UNIT_REQUIRED else null

    /** Adds the item when it is missing and removes it when it is there. */
    fun toggle(selected: Set<String>, item: String): Set<String> =
        if (item in selected) selected - item else selected + item

    /** The chosen resources in the list's own order, so the saved list does not depend on tap order. */
    fun orderedResources(selected: Set<String>): List<String> = RESOURCES.filter { it in selected }

    fun resourcesLabel(resources: List<String>): String =
        resources.joinToString(", ").ifBlank { NO_RESOURCES }

    fun label(assignment: Assignment): String =
        assignment.unit + " · " + resourcesLabel(assignment.assignedResources)
}
