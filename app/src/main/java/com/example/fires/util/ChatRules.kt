package com.example.fires.util

import com.example.fires.data.model.Role

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
    val isSending: Boolean
)

object ChatRules {
    const val MAX_LENGTH = 500
    const val SENDING_LABEL = "Sending…"

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
        isSending = sentAtMillis == null
    )
}
