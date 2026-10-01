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
}
