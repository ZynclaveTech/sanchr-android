package com.sanchr.feature.chats.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceWaveformTest {
    @Test
    fun `buckets average their slice and normalise to the loudest sample`() {
        val samples = listOf(0, 0, 100, 100, 50, 50, 25, 25)
        val bins = VoiceWaveform.bucket(samples, bins = 4)
        assertEquals(listOf(0f, 1f, 0.5f, 0.25f), bins)
    }

    @Test
    fun `never exceeds the sample count, and silence is all zeros rather than NaN`() {
        assertEquals(3, VoiceWaveform.bucket(listOf(1, 2, 3), bins = 40).size)
        assertTrue(VoiceWaveform.bucket(listOf(0, 0, 0), bins = 3).all { it == 0f })
        assertTrue(VoiceWaveform.bucket(emptyList()).isEmpty())
    }

    @Test
    fun `default bin count is the one iOS bubbles render comfortably`() {
        assertEquals(40, VoiceWaveform.bucket(List(4000) { it % 100 }).size)
    }
}
