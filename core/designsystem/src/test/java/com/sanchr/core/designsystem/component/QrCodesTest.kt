package com.sanchr.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class QrCodesTest {
    @Test
    fun `a profile link is the deep link iOS encodes, so either app reads the other's code`() {
        assertEquals("https://sanchr.com/u/user-42", QrCodes.profileLink("user-42"))
    }
}
