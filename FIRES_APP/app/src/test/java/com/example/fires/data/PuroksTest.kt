package com.example.fires.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PuroksTest {
    @Test
    fun list_hasAtLeastOneEntry() {
        assertFalse(Puroks.ALL.isEmpty())
    }

    @Test
    fun entries_areNotBlank() {
        assertFalse(Puroks.ALL.any { it.isBlank() })
    }

    @Test
    fun entries_areUnique() {
        assertEquals(Puroks.ALL.size, Puroks.ALL.toSet().size)
    }

    @Test
    fun entries_haveNoSurroundingSpaces() {
        // The saved value is compared with these exact strings.
        assertEquals(Puroks.ALL, Puroks.ALL.map { it.trim() })
    }
}
