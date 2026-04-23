package com.sanchr.core.model

import kotlin.test.assertEquals
import org.junit.Test

class IdsTest {
    @Test
    fun `value classes preserve underlying string`() {
        val u = UserId("user-123")
        val d = DeviceId("device-abc")
        assertEquals("user-123", u.value)
        assertEquals("device-abc", d.value)
    }

    @Test
    fun `MessageId equals by value`() {
        assertEquals(MessageId("m-1"), MessageId("m-1"))
    }
}
