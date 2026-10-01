package com.example.fires.util

import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Severity
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val nowMillis = 100_000_000L
    private val nowSec = nowMillis / 1000
    private val fresh = nowSec - 60              // a minute ago
    private val stale = nowSec - 31 * 60         // just past the 30 minute limit

    private fun inc(
        id: String,
        status: IncidentStatus = IncidentStatus.REPORTED,
        severity: Severity = Severity.MEDIUM,
        type: FireType = FireType.ELECTRICAL,
        address: String = "Purok 3, Bagumbayan",
        trapped: Boolean = false,
        duplicate: Boolean = false,
        submitted: Long? = fresh,
        updated: Long? = fresh
    ) = Incident(
        id = id, status = status.value, severity = severity.value, fireType = type.value,
        addressText = address, trapped = trapped, isDuplicate = duplicate,
        submittedAt = submitted?.let { Timestamp(it, 0) },
        updatedAt = updated?.let { Timestamp(it, 0) }
    )

    private fun detect(previous: List<Incident>?, current: List<Incident>) =
        AlertRules.detect(previous, current, nowMillis)

    // ---------- F1.1 What alerts ----------

    @Test fun firstSnapshot_onlySetsTheBaseline_noAlerts() {
        val existing = listOf(inc("a"), inc("b", severity = Severity.CRITICAL))
        assertTrue(detect(previous = null, current = existing).isEmpty())
    }

    @Test fun emptyBaseline_thenNewReport_alerts() {
        val events = detect(previous = emptyList(), current = listOf(inc("a")))
        assertEquals(1, events.size)
        assertEquals("a", events[0].incidentId)
        assertEquals(AlertKind.NEW_INCIDENT, events[0].kind)
    }

    @Test fun nothingChanged_noAlerts() {
        val list = listOf(inc("a"), inc("b", status = IncidentStatus.DISPATCHED))
        assertTrue(detect(list, list.map { it.copy() }).isEmpty())
    }

    @Test fun onlyTheNewIdAlerts_notTheExistingOnes() {
        val before = listOf(inc("a"), inc("b"))
        val events = detect(before, before + inc("c"))
        assertEquals(listOf("c"), events.map { it.incidentId })
    }

    @Test fun statusChange_alerts() {
        val before = listOf(inc("a", status = IncidentStatus.REPORTED))
        val after = listOf(inc("a", status = IncidentStatus.DISPATCHED))
        val events = detect(before, after)
        assertEquals(1, events.size)
        assertEquals(AlertKind.STATUS_CHANGED, events[0].kind)
        assertEquals("a", events[0].incidentId)
    }

    @Test fun changeToSomethingOtherThanStatus_doesNotAlert() {
        val before = listOf(inc("a", severity = Severity.LOW))
        val after = listOf(inc("a", severity = Severity.CRITICAL, address = "Somewhere else"))
        assertTrue(detect(before, after).isEmpty())
    }

    @Test fun incidentLeavingTheActiveList_doesNotAlert() {
        val before = listOf(inc("a"), inc("b"))
        assertTrue(detect(before, listOf(inc("b"))).isEmpty())
        assertTrue(detect(before, emptyList()).isEmpty())
    }

    @Test fun severalEvents_newReportsFirstMostSevereFirst_thenStatusChanges() {
        val before = listOf(inc("old", status = IncidentStatus.REPORTED))
        val after = listOf(
            inc("old", status = IncidentStatus.VERIFIED),
            inc("low", severity = Severity.LOW),
            inc("crit", severity = Severity.CRITICAL),
            inc("high", severity = Severity.HIGH)
        )
        assertEquals(listOf("crit", "high", "low", "old"), detect(before, after).map { it.incidentId })
    }

    // ---------- Too old to alert (stale snapshot after a restart) ----------

    @Test fun newReport_olderThanThirtyMinutes_doesNotAlert() {
        assertTrue(detect(emptyList(), listOf(inc("a", submitted = stale))).isEmpty())
    }

    @Test fun newReport_exactlyThirtyMinutesOld_stillAlerts() {
        val events = detect(emptyList(), listOf(inc("a", submitted = nowSec - 30 * 60)))
        assertEquals(1, events.size)
    }

    @Test fun newReport_withoutServerTimeYet_alerts() {
        assertEquals(1, detect(emptyList(), listOf(inc("a", submitted = null))).size)
    }

    @Test fun newReport_withTimeSlightlyInTheFuture_alerts() {
        // Phone clock behind the server clock.
        assertEquals(1, detect(emptyList(), listOf(inc("a", submitted = nowSec + 120))).size)
    }

    @Test fun statusChange_whoseUpdateIsOld_doesNotAlert() {
        val before = listOf(inc("a", status = IncidentStatus.REPORTED))
        val after = listOf(inc("a", status = IncidentStatus.DISPATCHED, updated = stale))
        assertTrue(detect(before, after).isEmpty())
    }

    @Test fun oldReport_withFreshStatusChange_alertsAsStatusChange() {
        // Submitted long ago, but a responder just moved it: that change is fresh news.
        val before = listOf(inc("a", status = IncidentStatus.VERIFIED, submitted = stale))
        val after = listOf(inc("a", status = IncidentStatus.DISPATCHED, submitted = stale, updated = fresh))
        assertEquals(1, detect(before, after).size)
    }

    // ---------- F1.2 Wording: new report ----------

    @Test fun newReport_isLoud_withSeverityInTitle_andTypeAndAddress() {
        val event = detect(emptyList(), listOf(
            inc("a", severity = Severity.CRITICAL, type = FireType.ELECTRICAL, address = "Purok 3, Bagumbayan")
        )).single()
        assertEquals(AlertLevel.LOUD, event.level)
        assertEquals("New fire report \u00B7 Critical", event.title)
        assertEquals("Electrical fire \u00B7 Purok 3, Bagumbayan", event.text)
    }

    @Test fun newReport_withTrappedPeople_saysSo() {
        val event = detect(emptyList(), listOf(inc("a", trapped = true))).single()
        assertEquals("Electrical fire \u00B7 Purok 3, Bagumbayan \u00B7 People trapped", event.text)
    }

    @Test fun newReport_withoutAddress_pointsToTheMap() {
        val event = detect(emptyList(), listOf(inc("a", address = "  "))).single()
        assertEquals("Electrical fire \u00B7 Open to see the map pin", event.text)
    }

    @Test fun newReport_ofTypeOther_saysJustFire() {
        val event = detect(emptyList(), listOf(inc("a", type = FireType.OTHER))).single()
        assertEquals("Fire \u00B7 Purok 3, Bagumbayan", event.text)
    }

    @Test fun newDuplicate_isWordedAsDuplicate_butStillLoud() {
        val event = detect(emptyList(), listOf(inc("a", duplicate = true, severity = Severity.HIGH))).single()
        assertEquals(AlertLevel.LOUD, event.level)
        assertEquals("Possible duplicate report \u00B7 High", event.title)
    }

    // ---------- F1.2 Wording: status change ----------

    private fun statusEvent(to: IncidentStatus, address: String = "Purok 3, Bagumbayan"): AlertEvent {
        val before = listOf(inc("a", status = IncidentStatus.REPORTED))
        val after = listOf(inc("a", status = to, address = address))
        return detect(before, after).single()
    }

    @Test fun statusChange_isQuiet_andShowsOnlyTheAddress() {
        val event = statusEvent(IncidentStatus.DISPATCHED)
        assertEquals(AlertLevel.QUIET, event.level)
        assertEquals("Incident dispatched", event.title)
        assertEquals("Purok 3, Bagumbayan", event.text)
    }

    @Test fun statusTitles_matchTheNewStatus() {
        assertEquals("Incident verified", statusEvent(IncidentStatus.VERIFIED).title)
        assertEquals("Incident dispatched", statusEvent(IncidentStatus.DISPATCHED).title)
        assertEquals("Responders on scene", statusEvent(IncidentStatus.ON_SCENE).title)
    }

    @Test fun statusChange_withoutAddress_pointsToTheMap() {
        assertEquals("Open to see the map pin", statusEvent(IncidentStatus.VERIFIED, address = "").text)
    }
}
