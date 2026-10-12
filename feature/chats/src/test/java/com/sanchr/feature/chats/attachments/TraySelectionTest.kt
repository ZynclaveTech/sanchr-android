package com.sanchr.feature.chats.attachments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Picking several photos from the attachment tray.
 *
 * The rules are iOS's: a long press starts a selection, taps add to it in
 * order, thirty is the ceiling, and unpicking the last one ends it.
 */
class TraySelectionTest {
    @Test
    fun `a long press starts a selection holding that photo`() {
        val selection = TraySelection().startedWith(7)

        assertTrue(selection.active)
        assertEquals(listOf(7L), selection.ids)
    }

    @Test
    fun `photos are kept in the order they were tapped`() {
        val selection =
            TraySelection()
                .startedWith(3)
                .toggled(1)
                .selection
                .toggled(2)
                .selection

        assertEquals(listOf(3L, 1L, 2L), selection.ids, "the send order is the tap order")
        assertEquals(1, selection.indexOf(1))
        assertNull(selection.indexOf(99))
    }

    @Test
    fun `tapping a picked photo unpicks it and the rest close ranks`() {
        val selection =
            TraySelection(ids = listOf(3, 1, 2), active = true).toggled(1).selection

        assertEquals(listOf(3L, 2L), selection.ids)
        assertEquals(1, selection.indexOf(2), "the badge after the gap must renumber")
        assertTrue(selection.active)
    }

    @Test
    fun `unpicking the last photo ends the selection`() {
        val selection = TraySelection().startedWith(5).toggled(5).selection

        assertFalse(selection.active)
        assertTrue(selection.ids.isEmpty())
    }

    @Test
    fun `a pick past the limit is refused and says so`() {
        val full = TraySelection(ids = (1L..TraySelection.LIMIT).toList(), active = true)

        val toggle = full.toggled(1000)

        assertTrue(toggle.refused)
        assertEquals(full, toggle.selection)
        assertTrue(full.atLimit)
    }

    @Test
    fun `a full selection can still give one up`() {
        val full = TraySelection(ids = (1L..TraySelection.LIMIT).toList(), active = true)

        val toggle = full.toggled(4)

        assertFalse(toggle.refused)
        assertEquals(TraySelection.LIMIT - 1, toggle.selection.ids.size)
    }

    @Test
    fun `an empty strip says why`() {
        assertEquals(TrayEmptyReason.DENIED, trayEmptyReason(MediaAccess.DENIED, null))
        assertEquals(TrayEmptyReason.DENIED, trayEmptyReason(MediaAccess.DENIED, emptyList<Int>()))
        assertEquals(TrayEmptyReason.LOADING, trayEmptyReason(MediaAccess.GRANTED, null))
        assertEquals(TrayEmptyReason.NO_PHOTOS, trayEmptyReason(MediaAccess.LIMITED, emptyList<Int>()))
        assertNull(trayEmptyReason(MediaAccess.GRANTED, listOf(1)))
    }

    @Test
    fun `a video's length reads as a clock`() {
        assertEquals("0:12", trayDurationLabel(12_000))
        assertEquals("1:05", trayDurationLabel(65_400))
        assertEquals("1:02:03", trayDurationLabel(3_723_000))
        assertEquals("—", trayDurationLabel(0), "an unknown length must not read as 0:00")
    }
}
