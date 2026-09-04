package com.sanchr.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Privacy screen's labels and the seconds that travel on the wire must
 * round-trip: a mismatch would silently select the wrong radio button, or
 * worse, persist a timer the user did not choose.
 */
class DisappearingDurationMappingTest {
    @Test
    fun `every offered label round-trips through seconds`() {
        listOf("off", "30s", "5m", "1h", "24h", "7d").forEach { label ->
            assertEquals(label, disappearingLabelOf(disappearingSecondsOf(label)), "round-trip for $label")
        }
    }

    @Test
    fun `labels map to the expected wire seconds`() {
        assertEquals(0, disappearingSecondsOf("off"))
        assertEquals(30, disappearingSecondsOf("30s"))
        assertEquals(300, disappearingSecondsOf("5m"))
        assertEquals(3_600, disappearingSecondsOf("1h"))
        assertEquals(86_400, disappearingSecondsOf("24h"))
        assertEquals(604_800, disappearingSecondsOf("7d"))
    }

    @Test
    fun `an unknown label is off rather than an arbitrary timer`() {
        assertEquals(0, disappearingSecondsOf("90m"))
    }

    @Test
    fun `an unrecognised stored value falls back to off rather than mis-selecting`() {
        // e.g. written by a future build offering more durations.
        assertEquals("off", disappearingLabelOf(12_345))
    }
}
