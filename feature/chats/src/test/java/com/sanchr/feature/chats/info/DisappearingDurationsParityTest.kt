package com.sanchr.feature.chats.info

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The per-chat timer set must match iOS's, or values are lost in both
 * directions: a 30-second chat set on Android matched no row on iOS, and a
 * 30-day chat set on iOS read as "Off" here — a chat that does expire,
 * displayed as one that does not.
 */
class DisappearingDurationsParityTest {
    @Test
    fun `the offered set matches iOS`() {
        assertEquals(listOf("off", "30s", "5m", "1h", "24h", "7d", "30d"), DisappearingDurations.LABELS)
    }

    @Test
    fun `every offered label round-trips`() {
        DisappearingDurations.LABELS.forEach { label ->
            assertEquals(label, DisappearingDurations.labelOf(DisappearingDurations.millisOf(label)), label)
        }
    }

    @Test
    fun `thirty days is a real duration, not off`() {
        val millis = assertNotNull(DisappearingDurations.millisOf("30d"))

        assertEquals(30L * 24 * 60 * 60 * 1_000, millis)
        assertEquals("30d", DisappearingDurations.labelOf(millis))
    }

    @Test
    fun `off stays null, so the account default still applies`() {
        assertNull(DisappearingDurations.millisOf("off"))
        assertEquals("off", DisappearingDurations.labelOf(null))
    }

    @Test
    fun `every label has display text of its own`() {
        DisappearingDurations.LABELS.forEach { label ->
            assertEquals(true, DisappearingDurations.displayName(label) != label, "no display name for $label")
        }
    }
}
