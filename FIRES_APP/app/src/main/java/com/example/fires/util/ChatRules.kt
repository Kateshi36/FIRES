package com.example.fires.util

import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.Role
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One row in the chat list, already worked out so the screen only has to draw it. */
data class ChatItem(
    val id: String,
    val text: String,
    val senderName: String,
    /** Sent by the person using this phone. Drawn on the right. */
    val isMine: Boolean,
    /** Sent by a responder or admin (not a citizen). Shown with a small "Responder" label. */
    val fromResponder: Boolean,
    /** "5 min ago", or "Sending…" while the message is only saved on this phone. */
    val timeLabel: String,
    val isSending: Boolean,
    /** When the server stamped it. Null while the message is only saved on this phone. */
    val sentAtMillis: Long? = null
)

object ChatRules {
    const val MAX_LENGTH = 500
    const val SENDING_LABEL = "Sending…"

    /** Shown instead of the message box once the report is resolved or dismissed. */
    const val CLOSED_TEXT_REPORTER =
        "This report is closed, so the chat is closed too. If there is a new fire, send a new report or call the barangay hotline."
    const val CLOSED_TEXT_RESPONDER = "This report is closed. The messages stay here to read, but no one can write in this chat anymore."

    fun closedText(isReporter: Boolean): String = if (isReporter) CLOSED_TEXT_REPORTER else CLOSED_TEXT_RESPONDER

    /**
     * The chat is open while the report is open. A resolved or dismissed report keeps its messages to read,
     * but nobody can write in it (firestore.rules refuses it as well). Until the report has loaded we do not
     * know, so the box stays; the server decides.
     */
    fun isChatClosed(status: IncidentStatus?): Boolean = status != null && !status.isActive

    /** What actually gets sent: leading/trailing blanks removed. */
    fun clean(text: String): String = text.trim()

    fun canSend(text: String): Boolean = clean(text).isNotEmpty()

    /** Stops typing at the limit instead of rejecting the message afterwards. */
    fun limit(text: String): String = if (text.length > MAX_LENGTH) text.take(MAX_LENGTH) else text

    fun item(
        id: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        text: String,
        /** Null while a just-sent message has no server time yet. */
        sentAtMillis: Long?,
        myUid: String?,
        nowMillis: Long,
        format: (Long) -> String = ::dateTimeLabel
    ) = ChatItem(
        id = id,
        text = text,
        senderName = senderName.ifBlank { "Unknown" },
        isMine = myUid != null && senderId == myUid,
        fromResponder = Role.fromValue(senderRole) != Role.CITIZEN,
        timeLabel = if (sentAtMillis == null) SENDING_LABEL else timeAgoLabel(sentAtMillis, nowMillis, format),
        isSending = sentAtMillis == null,
        sentAtMillis = sentAtMillis
    )

    /** One-tap texts the person who reported the fire can drop into the box. */
    val QUICK_REPLIES = listOf("I'm safe outside", "Someone is trapped", "Fire is spreading")

    const val RESPONDERS_NAME = "Responders"
    const val REPORTER_FALLBACK_NAME = "Reporter"

    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val day = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /** "9:44 PM" */
    fun clockLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** The small centred label above the first message of a day: "Today", "Yesterday" or "Oct 9". */
    fun dayLabel(millis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when (d) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(d)
        }
    }

    /**
     * Who the person is talking to, for the header: the newest message from the other side, else
     * the given fallback (the reporter's name for a responder, "Responders" for the reporter).
     */
    fun otherParty(items: List<ChatItem>, fallback: String): String =
        items.lastOrNull { !it.isMine }?.senderName ?: fallback
}
