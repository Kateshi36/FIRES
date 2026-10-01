package com.example.fires.data.model

/** "Hazards nearby" chips on the report form. Stored on the incident as the plain `value` strings. */
enum class HazardType(val value: String, val label: String) {
    LPG_TANK("lpg_tank", "LPG / gas tank"),
    CHEMICALS("chemicals", "Chemicals"),
    POWER_LINES("power_lines", "Power lines"),
    FUEL("fuel", "Fuel / gasoline");

    companion object {
        fun fromValue(v: String?): HazardType? = entries.firstOrNull { it.value == v }
    }
}
