package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTrackerTest {

    private val nowMillis = 100_000_000L
    private val fresh = Timestamp(nowMillis / 1000 - 60, 0)

    private fun inc(id: String, status: IncidentStatus = IncidentStatus.REPORTED) =
        Incident(id = id, status = status.value, submittedAt = fresh, updatedAt = fresh)

    private fun AlertTracker.server(vararg list: Incident) =
        onSnapshot(list.toList(), fromCache = false, nowMillis = nowMillis)

    private fun AlertTracker.cache(vararg list: Incident) =
        onSnapshot(list.toList(), fromCache = true, nowMillis = nowMillis)

    @Test fun firstServerSnapshot_isTheBaseline_noAlerts() {
        val tracker = AlertTracker()
        assertTrue(tracker.server(inc("a"), inc("b")).isEmpty())
    }

    @Test fun afterTheBaseline_aNewReportAlerts() {
        val tracker = AlertTracker()
        tracker.server(inc("a"))
        assertEquals(listOf("b"), tracker.server(inc("a"), inc("b")).map { it.incidentId })
    }

    @Test fun afterTheBaseline_aStatusChangeAlerts() {
        val tracker = AlertTracker()
        tracker.server(inc("a", IncidentStatus.REPORTED))
        val events = tracker.server(inc("a", IncidentStatus.DISPATCHED))
        assertEquals(listOf("a"), events.map { it.incidentId })
    }

    @Test fun sameDataAgain_metadataOnlyUpdate_noAlerts() {
        val tracker = AlertTracker()
        tracker.server(inc("a"))
        assertTrue(tracker.server(inc("a")).isEmpty())
    }

    // ---------- The saved copy on the phone ----------

    @Test fun cacheSnapshot_neverAlerts() {
        val tracker = AlertTracker()
        assertTrue(tracker.cache(inc("a")).isEmpty())
    }

    @Test fun cacheThenServer_theServerSnapshotIsTheBaseline_notADiffAgainstTheCache() {
        // Phone starts with an old saved copy; the server then reports fires made since.
        val tracker = AlertTracker()
        tracker.cache(inc("old"))
        assertTrue(tracker.server(inc("old"), inc("new1"), inc("new2")).isEmpty())
    }

    @Test fun cacheSnapshotInTheMiddle_keepsTheBaseline() {
        // Connection drops (a cache snapshot arrives), then comes back with one more fire.
        val tracker = AlertTracker()
        tracker.server(inc("a"))
        assertTrue(tracker.cache(inc("a")).isEmpty())
        assertEquals(listOf("b"), tracker.server(inc("a"), inc("b")).map { it.incidentId })
    }

    @Test fun cacheSnapshotInTheMiddle_doesNotReplaceTheBaselineWithStaleData() {
        val tracker = AlertTracker()
        tracker.server(inc("a"), inc("b"))
        tracker.cache() // stale and empty
        // If the empty cache snapshot had become the baseline, a and b would alert as "new" now.
        assertTrue(tracker.server(inc("a"), inc("b")).isEmpty())
    }

    // ---------- Reset ----------

    @Test fun reset_makesTheNextServerSnapshotABaselineAgain() {
        val tracker = AlertTracker()
        tracker.server(inc("a"))
        tracker.reset()
        assertTrue(tracker.server(inc("a"), inc("b")).isEmpty())
    }
}
