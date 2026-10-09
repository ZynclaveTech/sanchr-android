package com.sanchr.feature.chats

import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Date separators in the transcript.
 *
 * The screen drew one hardcoded "Today" chip above the whole list, so a
 * conversation going back months claimed every message in it was from today.
 */
class TranscriptRowsTest {
    private val locale = Locale.UK

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
    ): Long =
        Calendar
            .getInstance(locale)
            .apply {
                clear()
                set(year, month - 1, day, hour, 0, 0)
            }.timeInMillis

    private fun message(
        id: String,
        timestamp: Long,
    ) = MessageUiModel(
        id = id,
        text = id,
        timestamp = timestamp,
        isFromMe = false,
        status = MessageStatus.DELIVERED,
        contentType = "text",
    )

    private val now = at(2026, 10, 10)

    @Test
    fun `each day gets one separator, and it sits after that day's messages`() {
        val rows =
            transcriptRows(
                listOf(
                    message("a", at(2026, 10, 8, hour = 9)),
                    message("b", at(2026, 10, 8, hour = 18)),
                    message("c", at(2026, 10, 10, hour = 8)),
                ),
                now = now,
                locale = locale,
            )

        // List order is bottom-to-top, because the list is reverseLayout: the
        // newest message sits at index 0 and a day's separator follows its
        // messages so it renders above them. Read upwards this is
        // Thursday / a / b / Today / c.
        assertEquals(
            listOf("c", "Today", "b", "a", "Thursday"),
            rows.map { if (it is TranscriptRow.Day) it.label else (it as TranscriptRow.Msg).message.id },
        )
    }

    @Test
    fun `two messages on the same day share one separator`() {
        val rows =
            transcriptRows(
                listOf(message("a", at(2026, 10, 10, hour = 1)), message("b", at(2026, 10, 10, hour = 23))),
                now = now,
                locale = locale,
            )

        assertEquals(1, rows.count { it is TranscriptRow.Day })
    }

    @Test
    fun `messages arriving out of order are still grouped by their own day`() {
        val rows =
            transcriptRows(
                listOf(message("newer", at(2026, 10, 10)), message("older", at(2026, 10, 9))),
                now = now,
                locale = locale,
            )

        assertEquals(
            listOf("newer", "Today", "older", "Yesterday"),
            rows.map { if (it is TranscriptRow.Day) it.label else (it as TranscriptRow.Msg).message.id },
        )
    }

    @Test
    fun `an empty transcript has no separators`() {
        assertTrue(transcriptRows(emptyList(), now = now, locale = locale).isEmpty())
    }

    @Test
    fun `the label ladder runs today, yesterday, weekday, date, dated year`() {
        assertEquals("Today", dayLabel(at(2026, 10, 10, hour = 0), now, locale))
        assertEquals("Yesterday", dayLabel(at(2026, 10, 9, hour = 23), now, locale))
        assertEquals("Wednesday", dayLabel(at(2026, 10, 7), now, locale))
        assertEquals("1 October", dayLabel(at(2026, 10, 1), now, locale))
        assertEquals("25 December 2025", dayLabel(at(2025, 12, 25), now, locale))
    }

    @Test
    fun `two minutes either side of midnight are a day apart, not two minutes`() {
        // Elapsed time would call this "Today"; the separator is about dates.
        assertEquals("Yesterday", dayLabel(at(2026, 10, 9, hour = 23), at(2026, 10, 10, hour = 0), locale))
    }
}
