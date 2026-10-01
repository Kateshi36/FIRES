package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLabelsTest {
    private val now = 10_000_000_000L
    private fun ago(ms: Long) = timeAgoLabel(now - ms, now) { "DATE" }
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test fun underAMinute_isJustNow() {
        assertEquals("Just now", ago(0))
        assertEquals("Just now", ago(59_999))
    }

    @Test fun clockAheadOfServer_isJustNow() = assertEquals("Just now", ago(-5 * minute))

    @Test fun minutes() {
        assertEquals("1 min ago", ago(minute))
        assertEquals("59 min ago", ago(59 * minute + 30_000))
    }

    @Test fun hours() {
        assertEquals("1 h ago", ago(hour))
        assertEquals("23 h ago", ago(23 * hour + 59 * minute))
    }

    @Test fun aDayOrOlder_usesTheFullDate() {
        assertEquals("DATE", ago(24 * hour))
        assertEquals("DATE", ago(40 * hour))
    }
}
