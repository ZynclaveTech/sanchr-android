package com.sanchr.feature.chats

import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.MessageContent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The chat list's one-line summary of a conversation's newest message, with iOS `ConversationRow`'s wording. */
fun Conversation.previewText(): String {
    val last = lastMessage ?: return "No messages yet"
    return when (val content = last.content) {
        is MessageContent.Text -> content.body
        is MessageContent.Image -> "Photo"
        is MessageContent.Voice -> "Voice message"
        is MessageContent.File -> "Document"
        is MessageContent.Location -> "Location"
        is MessageContent.Contact -> "Contact: ${content.name}"
        is MessageContent.System -> content.text
    }
}

/** In a group, the incoming sender's first name as a prefix ("Ravi: "), as iOS; null for direct chats and own messages. */
fun Conversation.senderPrefix(currentUserId: String): String? {
    val last = lastMessage ?: return null
    if (type != ConversationType.GROUP || last.senderId == currentUserId) return null
    val name = participants.firstOrNull { it.id == last.senderId }?.displayName?.takeIf { it.isNotBlank() } ?: return null
    return name.substringBefore(' ') + ": "
}

/** Time for today, weekday within the last week, else a short date; iOS `chatTimestamp`. */
fun formatChatTimestamp(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
): String {
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val pattern =
        when {
            sameDay -> "h:mm a"
            now - epochMillis < WEEK_MS -> "EEE"
            then.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "d MMM"
            else -> "d MMM yyyy"
        }
    return SimpleDateFormat(pattern, locale).format(Date(epochMillis))
}

private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
