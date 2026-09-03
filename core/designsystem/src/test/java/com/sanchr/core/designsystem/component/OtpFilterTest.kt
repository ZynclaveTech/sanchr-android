package com.sanchr.core.designsystem.component

import kotlin.test.assertEquals
import org.junit.Test

class OtpFilterTest {
    @Test
    fun `filterOtpInput strips non-digits`() {
        assertEquals("1234", filterOtpInput("1a2b3c4d", maxLength = 6))
        assertEquals("", filterOtpInput("abcdef", maxLength = 6))
        assertEquals("56789", filterOtpInput("5-6 7.8,9", maxLength = 6))
    }

    @Test
    fun `filterOtpInput clamps to maxLength`() {
        assertEquals("123456", filterOtpInput("1234567890", maxLength = 6))
        assertEquals("12", filterOtpInput("123456", maxLength = 2))
        assertEquals("123456", filterOtpInput("12a34b56c78", maxLength = 6))
    }

    @Test
    fun `filterOtpInput preserves empty string`() {
        assertEquals("", filterOtpInput("", maxLength = 6))
    }
}
