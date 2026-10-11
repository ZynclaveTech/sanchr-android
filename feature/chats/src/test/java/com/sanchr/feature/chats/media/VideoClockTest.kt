package com.sanchr.feature.chats.media

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoClockTest {
    init {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `seconds are two digits and minutes are not padded`() {
        assertEquals("0:00", VideoClock.format(0))
        assertEquals("0:07", VideoClock.format(7_400))
        assertEquals("1:05", VideoClock.format(65_000))
        assertEquals("12:40", VideoClock.format(760_999))
    }

    @Test
    fun `an hour or more shows the hours`() {
        assertEquals("1:02:03", VideoClock.format(3_723_000))
    }

    /** The view reports -1 before it knows; that is not a time. */
    @Test
    fun `a position the player does not know yet reads as zero`() {
        assertEquals("0:00", VideoClock.format(-1))
    }
}
