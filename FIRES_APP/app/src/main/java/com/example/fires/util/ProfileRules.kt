package com.example.fires.util

/** Small pure helpers for the Profile screen. */
object ProfileRules {

    /** "Maria Santos" -> "MS"; one word -> its first letter; blank -> "?". */
    fun initials(fullName: String): String {
        val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    /** A detail the person has not filled in shows a dash instead of an empty line. */
    fun valueOrDash(value: String): String = value.trim().ifEmpty { "—" }
}
