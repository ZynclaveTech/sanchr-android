package com.sanchr.feature.chats

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One row of the transcript: a message, or the date separator above a day's
 * first message.
 *
 * The screen used to draw a single `Text("Today")` chip, pinned above the
 * list and unconditional, so a transcript going back months was labelled
 * "Today" once and never again. Grouping by day is arithmetic on timestamps,
 * which is exactly the part worth testing on its own, so it lives here rather
 * than inside the composable.
 */
sealed interface TranscriptRow {
    val key: String

    data class Day(
        val label: String,
        override val key: String,
    ) : TranscriptRow

    /**
     * "Unread messages", above the first message the user has not seen.
     *
     * iOS draws one from `firstUnreadMessageId`; Android had nothing, so
     * returning to a chat with forty new messages gave no indication of where
     * to start reading.
     */
    data object Unread : TranscriptRow {
        override val key: String get() = "unread-divider"
    }

    data class Msg(
        val message: MessageUiModel,
    ) : TranscriptRow {
        override val key: String get() = message.id
    }
}

/**
 * Rows newest-first, which is the order a `reverseLayout` list wants.
 *
 * @param unreadCount how many of the newest inbound messages the user has not
 *   read; the divider goes above the first of them. Zero draws none.
 */
fun transcriptRows(
    messages: List<MessageUiModel>,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    unreadCount: Int = 0,
): List<TranscriptRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = ArrayList<TranscriptRow>(messages.size + EXPECTED_DAYS)
    var lastDay: Int? = null
    var lastYear: Int? = null
    // The id of the first unread message: the oldest of the newest
    // `unreadCount` inbound ones. Counted over inbound messages only, because
    // our own messages are never unread.
    val firstUnreadId =
        if (unreadCount <= 0) {
            null
        } else {
            messages
                .sortedBy { it.timestamp }
                .filterNot { it.isFromMe }
                .takeLast(unreadCount)
                .firstOrNull()
                ?.id
        }
    // Oldest first while grouping, then reversed in one pass: a reverseLayout
    // list draws index 0 at the bottom, so a separator has to sit *after* the
    // day it labels to appear above it.
    messages.sortedBy { it.timestamp }.forEach { message ->
        val calendar = Calendar.getInstance(locale).apply { timeInMillis = message.timestamp }
        val day = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        if (day != lastDay || year != lastYear) {
            rows +=
                TranscriptRow.Day(
                    label = dayLabel(message.timestamp, now, locale),
                    key = "day-$year-$day",
                )
            lastDay = day
            lastYear = year
        }
        if (message.id == firstUnreadId) rows += TranscriptRow.Unread
        rows += TranscriptRow.Msg(message)
    }
    return rows.asReversed()
}

/**
 * "Today", "Yesterday", a weekday name within the past week, and a date
 * beyond that — the same ladder every messenger uses, because a bare date on
 * this morning's messages reads as a different conversation.
 */
fun dayLabel(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
): String {
    val days = calendarDaysBetween(epochMillis, now, locale)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Yesterday"
        days in 2..WEEK_DAYS -> SimpleDateFormat("EEEE", locale).format(Date(epochMillis))
        sameYear(epochMillis, now, locale) -> SimpleDateFormat("d MMMM", locale).format(Date(epochMillis))
        else -> SimpleDateFormat("d MMMM yyyy", locale).format(Date(epochMillis))
    }
}

/**
 * Whole calendar days between the two instants, not elapsed time: 23:59 and
 * 00:01 are a day apart even though two minutes separate them.
 */
private fun calendarDaysBetween(
    then: Long,
    now: Long,
    locale: Locale,
): Int {
    val start = midnight(then, locale)
    val end = midnight(now, locale)
    return ((end - start) / MILLIS_PER_DAY).toInt()
}

private fun midnight(
    epochMillis: Long,
    locale: Locale,
): Long =
    Calendar
        .getInstance(locale)
        .apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

private fun sameYear(
    then: Long,
    now: Long,
    locale: Locale,
): Boolean {
    fun year(value: Long) = Calendar.getInstance(locale).apply { timeInMillis = value }.get(Calendar.YEAR)
    return year(then) == year(now)
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val WEEK_DAYS = 6
private const val EXPECTED_DAYS = 8
