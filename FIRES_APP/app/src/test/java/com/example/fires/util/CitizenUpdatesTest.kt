package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitizenUpdatesTest {
    private fun report(id: String, status: IncidentStatus, updated: Long? = null, submitted: Long? = null) =
        Incident(
            id = id,
            status = status.value,
            updatedAt = updated?.let { Timestamp(it, 0) },
            submittedAt = submitted?.let { Timestamp(it, 0) }
        )

    @Test fun emptyListGivesNoUpdates() = assertTrue(CitizenUpdates.from(emptyList()).isEmpty())

    @Test fun newestChangeFirst() {
        val list = CitizenUpdates.from(
            listOf(
                report("aaaaaa111", IncidentStatus.REPORTED, updated = 100),
                report("bbbbbb222", IncidentStatus.DISPATCHED, updated = 300),
                report("cccccc333", IncidentStatus.RESOLVED, updated = 200)
            )
        )
        assertEquals(listOf("bbbbbb222", "cccccc333", "aaaaaa111"), list.map { it.incidentId })
    }

    @Test fun wordingMatchesTheStatusScreen() {
        val u = CitizenUpdates.from(listOf(report("abc12345", IncidentStatus.DISPATCHED, updated = 5))).single()
        assertEquals(statusHeadline(IncidentStatus.DISPATCHED), u.headline)
        assertEquals(statusDetail(IncidentStatus.DISPATCHED), u.detail)
        assertEquals(incidentCode("abc12345"), u.code)
    }

    @Test fun fallsBackToSubmittedTimeThenZero() {
        val u = CitizenUpdates.from(
            listOf(
                report("aaaaaa111", IncidentStatus.REPORTED, submitted = 50),
                report("bbbbbb222", IncidentStatus.REPORTED)
            )
        )
        assertEquals(50_000L, u.first().whenMillis)
        assertEquals(0L, u.last().whenMillis)
    }

    @Test fun listIsCapped() {
        val many = (1..50).map { report("id%05d".format(it), IncidentStatus.REPORTED, updated = it.toLong()) }
        assertEquals(CitizenUpdates.MAX_ITEMS, CitizenUpdates.from(many).size)
    }
}
