package com.sanchr.feature.chats.info

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisappearingDurationsTest {
    @Test
    fun `every offered label round-trips through milliseconds`() {
        DisappearingDurations.LABELS.forEach { label ->
            assertEquals(label, DisappearingDurations.labelOf(DisappearingDurations.millisOf(label)))
        }
    }

    @Test
    fun `off is null, not zero, so the account default still applies`() {
        assertNull(DisappearingDurations.millisOf("off"))
    }

    @Test
    fun `a duration this build has no label for reads as off rather than the wrong row`() {
        assertEquals("off", DisappearingDurations.labelOf(90_000L))
    }

    @Test
    fun `a non-positive stored duration reads as off`() {
        assertEquals("off", DisappearingDurations.labelOf(0L))
        assertEquals("off", DisappearingDurations.labelOf(-1L))
    }

    @Test
    fun `the durations are what their names say`() {
        assertEquals(30_000L, DisappearingDurations.millisOf("30s"))
        assertEquals(300_000L, DisappearingDurations.millisOf("5m"))
        assertEquals(3_600_000L, DisappearingDurations.millisOf("1h"))
        assertEquals(86_400_000L, DisappearingDurations.millisOf("24h"))
        assertEquals(604_800_000L, DisappearingDurations.millisOf("7d"))
    }

    @Test
    fun `an unknown label has no duration`() {
        assertNull(DisappearingDurations.millisOf("3y"))
    }
}
