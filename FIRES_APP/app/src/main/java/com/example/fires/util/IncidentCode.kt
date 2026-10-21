package com.example.fires.util

/** The short code shown for a report, the same one the administrator's website shows: INC-1A2B3C. */
fun incidentCode(id: String): String = "INC-" + id.take(6).uppercase()
