package com.sanchr.feature.chats.voice

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The loudness scale a sent waveform is drawn on.
 *
 * It used to divide each bucket by the clip's own loudest sample, which made
 * the scale relative to the clip: every note had one bar at full height, and a
 * whisper drew the same shape as a shout. iOS normalises its recorder's
 * samples against a fixed reference — `(clamp(dB, -60, 0) + 60) / 60` — so
 * the same clip has to draw the same bars on both.
 */
class VoiceWaveformTest {
    private fun assertClose(
        expected: Float,
        actual: Float,
        tolerance: Float = 0.01f,
    ) = assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected, was $actual")

    @Test
    fun `full scale is one and the floor is zero, as iOS clamps`() {
        assertClose(1f, VoiceWaveform.level(32_767))
        // -60 dB is the quietest the scale distinguishes.
        assertClose(0f, VoiceWaveform.level(32))
        assertEquals(0f, VoiceWaveform.level(0))
    }

    @Test
    fun `half amplitude is about 0 9, not 0 5 — the scale is in decibels`() {
        // 20*log10(0.5) = -6 dB, which is (−6+60)/60 ≈ 0.9.
        assertClose(0.9f, VoiceWaveform.level(16_383))
    }

    @Test
    fun `a quiet clip stays quiet instead of being stretched to full height`() {
        // Peak-relative normalisation gave this a 1.0 bar; it is a whisper.
        val quiet = VoiceWaveform.bucket(List(8) { 200 }, bins = 4)

        assertTrue(quiet.all { it < 0.6f }, "a whisper drew as a shout: $quiet")
        assertTrue(quiet.all { it > 0f })
    }

    @Test
    fun `a loud clip and a quiet one differ, where before they did not`() {
        val loud = VoiceWaveform.bucket(List(8) { 30_000 }, bins = 4)
        val quiet = VoiceWaveform.bucket(List(8) { 300 }, bins = 4)

        assertTrue(loud.first() > quiet.first() + 0.3f, "loud $loud vs quiet $quiet")
    }

    @Test
    fun `buckets average their slice, in order`() {
        val bins = VoiceWaveform.bucket(listOf(0, 0, 32_767, 32_767, 3_276, 3_276, 328, 328), bins = 4)

        assertEquals(4, bins.size)
        assertEquals(0f, bins[0])
        assertClose(1f, bins[1])
        // Each tenth of full scale is -20 dB, a third of the scale.
        assertClose(0.67f, bins[2], tolerance = 0.02f)
        assertClose(0.33f, bins[3], tolerance = 0.02f)
    }

    @Test
    fun `never exceeds the sample count, and silence is all zeros rather than NaN`() {
        assertEquals(3, VoiceWaveform.bucket(listOf(1, 2, 3), bins = 64).size)
        assertTrue(VoiceWaveform.bucket(listOf(0, 0, 0), bins = 3).all { it == 0f })
        assertTrue(VoiceWaveform.bucket(emptyList()).isEmpty())
    }

    @Test
    fun `the default bin count is the one iOS decodes`() {
        assertEquals(64, VoiceWaveform.DEFAULT_BINS)
        assertEquals(64, VoiceWaveform.bucket(List(4000) { it % 100 }).size)
    }
}
