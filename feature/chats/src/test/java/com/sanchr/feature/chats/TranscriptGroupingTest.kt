package com.sanchr.feature.chats

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs of messages from the same sender.
 *
 * iOS groups on sender, direction and a 180-second gap: 2pt between grouped
 * bubbles against 6pt otherwise, and one timestamp per run. Android spaced
 * every bubble equally and repeated the timestamp and tick under all of them,
 * which is most of why a busy conversation looked different from the same chat
 * on an iPhone.
 */
class TranscriptGroupingTest {
    private val base = 1_760_000_000_000L

    private fun message(
        id: String,
        fromMe: Boolean,
        atMillis: Long,
    ) = MessageUiModel(
        id = id,
        text = id,
        timestamp = atMillis,
        isFromMe = fromMe,
        status = MessageStatus.DELIVERED,
        contentType = "text",
    )

    private fun rowsOf(messages: List<MessageUiModel>) =
        transcriptRows(messages, now = base + 1_000_000)
            .filterIsInstance<TranscriptRow.Msg>()
            .associateBy { it.message.id }

    @Test
    fun `consecutive messages from the same sender within three minutes group`() {
        val rows =
            rowsOf(
                listOf(
                    message("first", fromMe = false, atMillis = base),
                    message("second", fromMe = false, atMillis = base + 30_000),
                ),
            )

        assertFalse(rows.getValue("first").groupedWithPrevious)
        assertTrue(rows.getValue("first").groupedWithNext, "the first of a run must know a run follows")
        assertTrue(rows.getValue("second").groupedWithPrevious)
        assertFalse(rows.getValue("second").groupedWithNext)
    }

    @Test
    fun `a gap of more than three minutes breaks the run`() {
        val rows =
            rowsOf(
                listOf(
                    message("early", fromMe = false, atMillis = base),
                    message("later", fromMe = false, atMillis = base + 181_000),
                ),
            )

        assertFalse(rows.getValue("later").groupedWithPrevious)
        assertFalse(rows.getValue("early").groupedWithNext)
    }

    @Test
    fun `the other direction breaks the run, however close in time`() {
        val rows =
            rowsOf(
                listOf(
                    message("theirs", fromMe = false, atMillis = base),
                    message("mine", fromMe = true, atMillis = base + 1_000),
                ),
            )

        assertFalse(rows.getValue("mine").groupedWithPrevious)
        assertFalse(rows.getValue("theirs").groupedWithNext)
    }

    @Test
    fun `exactly three minutes apart does not group, as iOS compares`() {
        val rows =
            rowsOf(
                listOf(
                    message("a", fromMe = true, atMillis = base),
                    message("b", fromMe = true, atMillis = base + 180_000),
                ),
            )

        assertFalse(rows.getValue("b").groupedWithPrevious)
    }

    @Test
    fun `the first unread message never groups with what came before it`() {
        // The divider sits between them, so grouping across it would read as
        // one run split by a line.
        val rows =
            transcriptRows(
                listOf(
                    message("read", fromMe = false, atMillis = base),
                    message("unread", fromMe = false, atMillis = base + 1_000),
                ),
                now = base + 1_000_000,
                unreadCount = 1,
            ).filterIsInstance<TranscriptRow.Msg>().associateBy { it.message.id }

        assertFalse(rows.getValue("unread").groupedWithPrevious)
    }

    @Test
    fun `a middle message of a long run is grouped on both sides`() {
        val rows =
            rowsOf(
                listOf(
                    message("one", fromMe = true, atMillis = base),
                    message("two", fromMe = true, atMillis = base + 10_000),
                    message("three", fromMe = true, atMillis = base + 20_000),
                ),
            )

        assertTrue(rows.getValue("two").groupedWithPrevious)
        assertTrue(rows.getValue("two").groupedWithNext)
    }

    /**
     * The spacing the grouping exists to drive. The list's comment described
     * it and no code applied it, so on a phone two bubbles of one run touched.
     */
    @Test
    fun `a grouped row takes iOS's 2 and any other row its 6`() {
        assertEquals(2.dp, bubbleRowMargin(groupedWithPrevious = true))
        assertEquals(6.dp, bubbleRowMargin(groupedWithPrevious = false))
    }

    @Test
    fun `two bubbles of one run are never left touching`() {
        val rows =
            rowsOf(
                listOf(
                    message("first", fromMe = false, atMillis = base),
                    message("second", fromMe = false, atMillis = base + 30_000),
                ),
            )
        val gap =
            bubbleRowMargin(rows.getValue("first").groupedWithPrevious) +
                bubbleRowMargin(rows.getValue("second").groupedWithPrevious)

        assertEquals(8.dp, gap, "the first row's bottom margin plus the grouped row's top margin")
    }
}
