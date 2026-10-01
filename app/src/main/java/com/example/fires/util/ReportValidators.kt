package com.example.fires.util

import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType

/**
 * Required-field rules for the report form (D2). Pure Kotlin, so it runs as a plain JVM test.
 * Every check returns null when fine, or the message to show under the field.
 *
 * Required: type of fire, description, fire size, and a location (GPS or pin).
 * Optional: people at risk (defaults to 0), vulnerable persons, trapped, hazards.
 */
object ReportValidators {

    const val MIN_DESCRIPTION = 10
    const val MAX_DESCRIPTION = 500
    const val MAX_PEOPLE = 99

    const val MSG_TYPE_MISSING = "Choose the type of fire."
    const val MSG_DESCRIPTION_BLANK = "Describe what is burning and what you see."
    const val MSG_DESCRIPTION_SHORT = "Add a little more detail (at least $MIN_DESCRIPTION characters)."
    const val MSG_SIZE_MISSING = "Choose how big the fire is."
    const val MSG_LOCATION_MISSING = "Set where the fire is. Use your GPS or move the pin on the map."

    data class ReportErrors(
        val fireType: String? = null,
        val description: String? = null,
        val fireSize: String? = null,
        val location: String? = null
    ) {
        val hasErrors: Boolean
            get() = fireType != null || description != null || fireSize != null || location != null
    }

    fun fireType(value: FireType?): String? = if (value == null) MSG_TYPE_MISSING else null

    fun description(value: String): String? {
        val text = value.trim()
        return when {
            text.isEmpty() -> MSG_DESCRIPTION_BLANK
            text.length < MIN_DESCRIPTION -> MSG_DESCRIPTION_SHORT
            else -> null
        }
    }

    fun fireSize(value: FireSize?): String? = if (value == null) MSG_SIZE_MISSING else null

    fun location(hasLocation: Boolean): String? = if (hasLocation) null else MSG_LOCATION_MISSING

    fun validate(
        fireType: FireType?,
        description: String,
        fireSize: FireSize?,
        hasLocation: Boolean
    ) = ReportErrors(
        fireType = fireType(fireType),
        description = description(description),
        fireSize = fireSize(fireSize),
        location = location(hasLocation)
    )
}
