package com.sanchr.feature.calls

import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class CallTimeFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** 2026-10-11 12:00:00 UTC, a Sunday. */
    private val nowSeconds = 1_791_720_000L
    private val nowMillis = nowSeconds * 1_000

    private fun label(startedAtSeconds: Long) = CallTimeFormat.listLabel(startedAtSeconds, nowMillis, Locale.US, utc)

    /** The bug: seconds read as milliseconds put every call in January 1970. */
    @Test
    fun `a call an hour ago shows its time, not a date in 1970`() {
        assertEquals("11:00 AM", label(nowSeconds - 3_600))
    }

    @Test
    fun `a call earlier this week shows the weekday`() {
        assertEquals("Thu", label(nowSeconds - 3 * 86_400))
    }

    @Test
    fun `an older call shows the date`() {
        assertEquals("Sep 11", label(nowSeconds - 30 * 86_400))
    }

    @Test
    fun `just under a day is still a time, and a day is a weekday`() {
        assertEquals("12:00 PM", label(nowSeconds - 86_400 + 1).takeLast(8))
        assertEquals("Sat", label(nowSeconds - 86_400))
    }

    @Test
    fun `a call with no time says nothing rather than inventing one`() {
        assertEquals("", label(0))
        assertEquals("", label(-5))
    }

    @Test
    fun `the label follows the zone it is asked for`() {
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")

        assertEquals("4:30 PM", CallTimeFormat.listLabel(nowSeconds - 3_600, nowMillis, Locale.US, kolkata))
    }
}
