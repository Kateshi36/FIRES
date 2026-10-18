package com.example.fires.util

import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Message
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F2: citizen notifications for status changes and replies. */
class CitizenAlertsTest {

    private val nowMillis = 100_000_000L
    private val fresh = Timestamp(nowMillis / 1000 - 60, 0)
    private val stale = Timestamp(nowMillis / 1000 - 3 * 60 * 60, 0)
    private val me = "citizen-1"

    private fun inc(id: String, status: IncidentStatus = IncidentStatus.REPORTED, updatedAt: Timestamp? = fresh) =
        Incident(id = id, status = status.value, submittedAt = fresh, updatedAt = updatedAt)

    private fun msg(
        id: String,
        from: String = "resp-1",
        role: String = "responder",
        text: String = "Are you safe?",
        name: String = "Ana",
        sentAt: Timestamp? = fresh
    ) = Message(id = id, senderId = from, senderName = name, senderRole = role, messageText = text, sentAt = sentAt)

    private fun CitizenStatusTracker.server(vararg l: Incident) = onSnapshot(l.toList(), false, nowMillis)
    private fun CitizenStatusTracker.cache(vararg l: Incident) = onSnapshot(l.toList(), true, nowMillis)
    private fun ReplyTracker.server(vararg l: Message) = onSnapshot(l.toList(), false, me, nowMillis)
    private fun ReplyTracker.cache(vararg l: Message) = onSnapshot(l.toList(), true, me, nowMillis)

    // ---------- Status changes ----------

    @Test fun status_firstServerSnapshot_isTheBaseline_noAlert() =
        assertTrue(CitizenStatusTracker().server(inc("a")).isEmpty())

    @Test fun status_change_alertsWithTheStatusScreenWording() {
        val t = CitizenStatusTracker()
        t.server(inc("a", IncidentStatus.REPORTED))
        val alert = t.server(inc("a", IncidentStatus.DISPATCHED)).single()
        assertEquals("a", alert.incidentId)
        assertEquals(CitizenAlertKind.STATUS, alert.kind)
        assertEquals(statusHeadline(IncidentStatus.DISPATCHED), alert.title)
        assertEquals(statusDetail(IncidentStatus.DISPATCHED), alert.text)
    }

    @Test fun status_resolvedAndDismissed_alertToo() {
        val t = CitizenStatusTracker()
        t.server(inc("a", IncidentStatus.ON_SCENE), inc("b", IncidentStatus.REPORTED))
        val alerts = t.server(inc("a", IncidentStatus.RESOLVED), inc("b", IncidentStatus.DISMISSED))
        assertEquals(listOf("a", "b"), alerts.map { it.incidentId })
    }

    @Test fun status_sameStatusAgain_noAlert() {
        val t = CitizenStatusTracker()
        t.server(inc("a"))
        assertTrue(t.server(inc("a")).isEmpty())
    }

    @Test fun status_aReportThatJustAppeared_neverAlerts() {
        val t = CitizenStatusTracker()
        t.server(inc("a"))
        assertTrue(t.server(inc("a"), inc("b")).isEmpty())
    }

    @Test fun status_staleUpdate_noAlert() {
        val t = CitizenStatusTracker()
        t.server(inc("a", IncidentStatus.REPORTED))
        assertTrue(t.server(inc("a", IncidentStatus.VERIFIED, updatedAt = stale)).isEmpty())
    }

    @Test fun status_cacheSnapshot_neverAlerts_andIsNotTheBaseline() {
        val t = CitizenStatusTracker()
        assertTrue(t.cache(inc("a", IncidentStatus.REPORTED)).isEmpty())
        // The first SERVER snapshot is the baseline, so a change made while closed does not alert.
        assertTrue(t.server(inc("a", IncidentStatus.DISPATCHED)).isEmpty())
    }

    @Test fun status_cacheInTheMiddle_keepsTheBaseline() {
        val t = CitizenStatusTracker()
        t.server(inc("a", IncidentStatus.REPORTED))
        assertTrue(t.cache().isEmpty()) // stale and empty
        assertEquals(1, t.server(inc("a", IncidentStatus.VERIFIED)).size)
    }

    // ---------- Replies ----------

    @Test fun reply_firstServerSnapshot_isTheBaseline_noAlert() =
        assertNull(ReplyTracker("a").server(msg("m1")))

    @Test fun reply_fromAResponder_alerts() {
        val t = ReplyTracker("a")
        t.server(msg("m1", from = me, role = "citizen"))
        val alert = t.server(msg("m1", from = me, role = "citizen"), msg("m2", text = "Stay outside"))!!
        assertEquals("a", alert.incidentId)
        assertEquals(CitizenAlertKind.REPLY, alert.kind)
        assertEquals("Reply from Ana", alert.title)
        assertEquals("Stay outside", alert.text)
    }

    @Test fun reply_adminCountsAsAResponder() {
        val t = ReplyTracker("a")
        t.server()
        assertNotNull(t.server(msg("m1", role = "admin")))
    }

    @Test fun reply_myOwnMessage_neverAlerts_evenWhileItIsStillSending() {
        val t = ReplyTracker("a")
        t.server()
        assertNull(t.server(msg("m1", from = me, role = "citizen", sentAt = null)))
    }

    @Test fun reply_anotherCitizen_neverAlerts() {
        val t = ReplyTracker("a")
        t.server()
        assertNull(t.server(msg("m1", from = "citizen-2", role = "citizen")))
    }

    @Test fun reply_severalAtOnce_oneAlertWithACountAndTheNewestText() {
        val t = ReplyTracker("a")
        t.server()
        val alert = t.server(msg("m1", text = "first"), msg("m2", text = "second"))!!
        assertEquals("2 new replies from responders", alert.title)
        assertEquals("second", alert.text)
    }

    @Test fun reply_blankName_saysAResponder() {
        val t = ReplyTracker("a")
        t.server()
        assertEquals("Reply from a responder", t.server(msg("m1", name = " "))!!.title)
    }

    @Test fun reply_longText_isCut() {
        val t = ReplyTracker("a")
        t.server()
        assertEquals(300, t.server(msg("m1", text = "x".repeat(500)))!!.text.length)
    }

    @Test fun reply_staleMessage_noAlert() {
        val t = ReplyTracker("a")
        t.server()
        assertNull(t.server(msg("m1", sentAt = stale)))
    }

    @Test fun reply_cacheSnapshot_neverAlerts_andIsNotTheBaseline() {
        val t = ReplyTracker("a")
        assertNull(t.cache(msg("m1")))
        // Sent while the app was closed: part of the first server snapshot, so it does not alert.
        assertNull(t.server(msg("m1"), msg("m2")))
    }

    @Test fun reply_cacheInTheMiddle_keepsTheBaseline() {
        val t = ReplyTracker("a")
        t.server(msg("m1"))
        assertNull(t.cache()) // stale and empty
        assertNull(t.server(msg("m1"))) // m1 must not look new
        assertNotNull(t.server(msg("m1"), msg("m2")))
    }

    // ---------- Not notifying about what is already on screen ----------

    private val statusAlert = CitizenAlert("a", CitizenAlertKind.STATUS, "t", "x")
    private val replyAlert = CitizenAlert("a", CitizenAlertKind.REPLY, "t", "x")

    @Test fun suppressed_statusWhileOnThatStatusScreen() =
        assertTrue(CitizenAlertRules.isSuppressed(statusAlert, Viewing(ViewedScreen.STATUS, "a")))

    @Test fun suppressed_replyWhileInThatChat() =
        assertTrue(CitizenAlertRules.isSuppressed(replyAlert, Viewing(ViewedScreen.CHAT, "a")))

    @Test fun notSuppressed_statusWhileOnlyInTheChat_orReplyWhileOnlyOnStatus() {
        assertFalse(CitizenAlertRules.isSuppressed(statusAlert, Viewing(ViewedScreen.CHAT, "a")))
        assertFalse(CitizenAlertRules.isSuppressed(replyAlert, Viewing(ViewedScreen.STATUS, "a")))
    }

    @Test fun notSuppressed_forAnotherReport_orNothingOpen() {
        assertFalse(CitizenAlertRules.isSuppressed(statusAlert, Viewing(ViewedScreen.STATUS, "b")))
        assertFalse(CitizenAlertRules.isSuppressed(statusAlert, Viewing()))
    }

    // ---------- Which chats to listen to ----------

    @Test fun watch_onlyOpenReports() {
        val list = listOf(
            inc("open1", IncidentStatus.DISPATCHED), inc("done", IncidentStatus.RESOLVED),
            inc("gone", IncidentStatus.DISMISSED), inc("open2", IncidentStatus.REPORTED)
        )
        assertEquals(setOf("open1", "open2"), CitizenWatchRules.threadsToWatch(list))
    }

    @Test fun watch_isCapped() {
        val many = (1..25).map { inc("i$it") }
        assertEquals(CitizenWatchRules.MAX_WATCHED_THREADS, CitizenWatchRules.threadsToWatch(many).size)
    }

    // ---------- Tap target ----------

    @Test fun target_waitsWhileStarting_opensInCitizenArea_dropsElsewhere() {
        assertEquals(TargetAction.WAIT, CitizenTargetRules.decide(TargetPlace.STARTING))
        assertEquals(TargetAction.OPEN, CitizenTargetRules.decide(TargetPlace.CITIZEN_AREA))
        assertEquals(TargetAction.DROP, CitizenTargetRules.decide(TargetPlace.RESPONDER_AREA))
        assertEquals(TargetAction.DROP, CitizenTargetRules.decide(TargetPlace.ELSEWHERE))
    }

    @Test fun responderTarget_isNeverOpenedInTheCitizenArea() =
        assertEquals(TargetAction.DROP, AlertTargetRules.decide(TargetPlace.CITIZEN_AREA))

    @Test fun screen_parsesItsOwnValue_andRejectsOthers() {
        assertEquals(CitizenScreen.CHAT, CitizenScreen.fromValue("chat"))
        assertEquals(CitizenScreen.STATUS, CitizenScreen.fromValue("status"))
        assertNull(CitizenScreen.fromValue(null))
        assertNull(CitizenScreen.fromValue("x"))
    }
}
