package com.example.fires.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

/** Full date and time in the phone's time zone, e.g. "3 Oct 2026, 2:15 PM". */
fun dateTimeLabel(millis: Long): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * "Just now", "5 min ago", "3 h ago", then the full date once it is a day old.
 * [format] is only used for the full date, so tests do not depend on the time zone.
 */
fun timeAgoLabel(
    thenMillis: Long,
    nowMillis: Long,
    format: (Long) -> String = ::dateTimeLabel
): String {
    val minutes = (nowMillis - thenMillis) / 60_000
    return when {
        minutes < 1 -> "Just now" // also covers a phone clock that is slightly behind the server
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> format(thenMillis)
    }
}
