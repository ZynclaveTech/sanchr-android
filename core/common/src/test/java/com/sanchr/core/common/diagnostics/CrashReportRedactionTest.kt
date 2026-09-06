package com.sanchr.core.common.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportRedactionTest {
    @Test
    fun `a phone number never survives`() {
        val out = CrashReportRedaction.redact("failed to send to +91 98765 43210")
        assertFalse(out.contains("98765"), out)
        assertTrue(out.contains("[phone]"), out)
    }

    @Test
    fun `an email never survives`() {
        val out = CrashReportRedaction.redact("lookup failed for ada@example.com")
        assertFalse(out.contains("ada@"), out)
    }

    @Test
    fun `a content uri never survives`() {
        val out = CrashReportRedaction.redact("could not open content://media/external/images/42")
        assertFalse(out.contains("media/external"), out)
    }

    @Test
    fun `a file path never survives`() {
        val out = CrashReportRedaction.redact("missing /data/user/0/com.sanchr.app/cache/photo.jpg")
        assertFalse(out.contains("com.sanchr.app/cache"), out)
    }

    @Test
    fun `a long base64 run, which could be key material, never survives`() {
        val out = CrashReportRedaction.redact("bad key MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE")
        assertFalse(out.contains("MFkwEwYHKoZIzj0"), out)
    }

    @Test
    fun `ordinary text is left readable`() {
        assertEquals("attachment is empty", CrashReportRedaction.redact("attachment is empty"))
    }

    @Test
    fun `null and blank collapse to empty rather than the word null`() {
        assertEquals("", CrashReportRedaction.redact(null))
        assertEquals("", CrashReportRedaction.redact("   "))
    }

    @Test
    fun `a summary carries the type and site but no message`() {
        val boom = IllegalStateException("token for +919876543210 expired")

        val summary = CrashReportRedaction.summarise(boom)

        assertTrue(summary.startsWith("java.lang.IllegalStateException at "), summary)
        assertFalse(summary.contains("9876543210"), summary)
    }
}
