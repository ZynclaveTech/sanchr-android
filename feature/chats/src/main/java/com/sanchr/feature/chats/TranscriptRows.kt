package com.sanchr.feature.chats

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        /**
         * Whether the message above this one is from the same sender, close
         * enough in time to read as one run.
         *
         * iOS groups on sender, direction and a 180-second gap, drawing 2pt
         * between grouped bubbles against 6pt otherwise and showing one
         * timestamp per run. Android spaced every bubble equally and repeated
         * the timestamp and tick on all of them, which is most of why a busy
         * conversation looked different from the same chat on an iPhone.
         */
        val groupedWithPrevious: Boolean = false,
        val groupedWithNext: Boolean = false,
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
    return rows.withGrouping(firstUnreadId).asReversed()
}

/**
 * Marks runs of messages from the same sender, oldest-first input.
 *
 * iOS's rule exactly: same sender, same direction, less than
 * [GROUPING_WINDOW_MS] apart, and never grouped across the unread divider,
 * which separates them visually anyway.
 */
private fun List<TranscriptRow>.withGrouping(firstUnreadId: String?): List<TranscriptRow> {
    fun joins(
        earlier: MessageUiModel?,
        later: MessageUiModel,
    ): Boolean {
        if (earlier == null) return false
        if (later.id == firstUnreadId) return false
        return earlier.isFromMe == later.isFromMe &&
            later.timestamp - earlier.timestamp < GROUPING_WINDOW_MS
    }

    val messages = mapIndexedNotNull { index, row -> (row as? TranscriptRow.Msg)?.let { index to it.message } }
    val grouping = HashMap<String, Pair<Boolean, Boolean>>(messages.size)
    messages.forEachIndexed { position, (_, message) ->
        val previous = messages.getOrNull(position - 1)?.second
        val next = messages.getOrNull(position + 1)?.second
        grouping[message.id] = joins(previous, message) to (next != null && joins(message, next))
    }
    return map { row ->
        if (row is TranscriptRow.Msg) {
            val (withPrevious, withNext) = grouping[row.message.id] ?: (false to false)
            row.copy(groupedWithPrevious = withPrevious, groupedWithNext = withNext)
        } else {
            row
        }
    }
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

/** iOS's `groupingThreshold`: 180 seconds. */
private const val GROUPING_WINDOW_MS = 180_000L

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val WEEK_DAYS = 6
private const val EXPECTED_DAYS = 8

/**
 * The margin above and below one message row.
 *
 * iOS's rule exactly — `.margins(.vertical, isGroupedWithPrev ? 2 : 6)`. The
 * margins of two neighbours add up, so a run's second bubble sits 8dp under
 * its first, later ones 4dp apart, and two unrelated bubbles 12dp apart.
 */
fun bubbleRowMargin(groupedWithPrevious: Boolean): Dp = if (groupedWithPrevious) GROUPED_ROW_MARGIN else UNGROUPED_ROW_MARGIN

private val GROUPED_ROW_MARGIN = 2.dp
private val UNGROUPED_ROW_MARGIN = 6.dp
