package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRulesTest {

    private val now = 1_000_000_000L
    private fun item(
        senderId: String = "u1", name: String = "Ana", role: String = "citizen",
        sentAt: Long? = now - 120_000, me: String? = "u1"
    ) = ChatRules.item("m1", senderId, name, role, "hello", sentAt, me, now) { "DATE" }

    @Test fun blankMessage_cannotBeSent() {
        assertFalse(ChatRules.canSend(""))
        assertFalse(ChatRules.canSend("   \n "))
    }

    @Test fun realMessage_canBeSent_andIsTrimmed() {
        assertTrue(ChatRules.canSend(" help "))
        assertEquals("help", ChatRules.clean("  help \n"))
    }

    @Test fun limit_cutsAtMaxLength_butLeavesShortTextAlone() {
        assertEquals(ChatRules.MAX_LENGTH, ChatRules.limit("a".repeat(ChatRules.MAX_LENGTH + 50)).length)
        assertEquals("short", ChatRules.limit("short"))
    }

    @Test fun myMessage_isMine_otherPersonsIsNot() {
        assertTrue(item(senderId = "u1", me = "u1").isMine)
        assertFalse(item(senderId = "u2", me = "u1").isMine)
    }

    @Test fun nobodySignedIn_nothingIsMine() = assertFalse(item(me = null).isMine)

    @Test fun responderAndAdmin_areMarkedAsResponders_citizenIsNot() {
        assertTrue(item(role = "responder").fromResponder)
        assertTrue(item(role = "admin").fromResponder)
        assertFalse(item(role = "citizen").fromResponder)
    }

    @Test fun unknownRole_isTreatedAsCitizen() = assertFalse(item(role = "???").fromResponder)

    @Test fun messageWithoutServerTime_isShownAsSending() {
        val i = item(sentAt = null)
        assertTrue(i.isSending)
        assertEquals(ChatRules.SENDING_LABEL, i.timeLabel)
    }

    @Test fun messageWithServerTime_showsHowLongAgo() {
        val i = item(sentAt = now - 120_000)
        assertFalse(i.isSending)
        assertEquals("2 min ago", i.timeLabel)
    }

    @Test fun blankSenderName_getsAFallback() = assertEquals("Unknown", item(name = " ").senderName)

    @Test fun clock_and_day_labels() {
        val utc = java.time.ZoneId.of("UTC")
        fun ms(d: Int, h: Int) = java.time.ZonedDateTime.of(2026, 10, d, h, 5, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("9:05 PM", ChatRules.clockLabel(ms(22, 21), utc))
        assertEquals("Today", ChatRules.dayLabel(ms(22, 1), ms(22, 23), utc))
        assertEquals("Yesterday", ChatRules.dayLabel(ms(21, 23), ms(22, 1), utc))
        assertEquals("Oct 9", ChatRules.dayLabel(ms(9, 12), ms(22, 12), utc))
    }

    @Test fun the_header_names_the_other_side() {
        val mine = ChatItem("1", "hi", "Me", true, false, "x", false)
        val theirs = ChatItem("2", "hello", "Responder Reyes", false, true, "x", false)
        assertEquals("Responder Reyes", ChatRules.otherParty(listOf(mine, theirs), ChatRules.RESPONDERS_NAME))
        assertEquals("Responders", ChatRules.otherParty(listOf(mine), ChatRules.RESPONDERS_NAME))
    }

    @Test fun there_are_three_quick_replies_and_all_fit_the_limit() {
        assertEquals(3, ChatRules.QUICK_REPLIES.size)
        assertTrue(ChatRules.QUICK_REPLIES.all { ChatRules.canSend(it) && it.length <= ChatRules.MAX_LENGTH })
    }
}
