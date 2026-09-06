package com.sanchr.feature.chats.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule that keeps a photograph from ending the process.
 *
 * Pure arithmetic, so it is tested directly rather than by decoding: the
 * question is only ever whether the chosen sample size brings the allocation
 * under budget, and that is where the mistake would be.
 */
class BoundedBitmapsTest {
    private val budget = BoundedBitmaps.DEFAULT_MAX_BYTES

    @Test
    fun `an ordinary photo is decoded whole`() {
        // 12 MP is 48 MB as ARGB — under budget, so no detail is thrown away.
        assertEquals(1, BoundedBitmaps.sampleSizeFor(4000, 3000))
    }

    @Test
    fun `a modern phone photo is downsampled rather than allocated whole`() {
        // 108 MP: 432 MB as ARGB. This is a normal photo from a normal phone,
        // not an attack, and it used to be decoded in full on the UI thread.
        val sample = BoundedBitmaps.sampleSizeFor(12000, 9000)
        assertTrue(sample > 1, "a 108 MP photo was not downsampled")
        assertUnderBudget(12000, 9000, sample)
    }

    @Test
    fun `a decompression bomb is brought under budget`() {
        // 30000x30000 compresses to very little and asks for 3.6 GB.
        val sample = BoundedBitmaps.sampleSizeFor(30000, 30000)
        assertUnderBudget(30000, 30000, sample)
    }

    /**
     * BitmapFactory rounds a non-power-of-two sample size down to 1, so an
     * "almost right" value is the same as no limit at all.
     */
    @Test
    fun `the sample size is always a power of two`() {
        for (side in listOf(5000, 9000, 12000, 20000, 30000)) {
            val sample = BoundedBitmaps.sampleSizeFor(side, side)
            assertTrue(sample > 0 && (sample and (sample - 1)) == 0, "$side gave $sample")
        }
    }

    @Test
    fun `a smaller budget downsamples more`() {
        val generous = BoundedBitmaps.sampleSizeFor(8000, 6000, maxBytes = budget)
        val tight = BoundedBitmaps.sampleSizeFor(8000, 6000, maxBytes = 4L * 1024 * 1024)
        assertTrue(tight > generous, "tight=$tight generous=$generous")
    }

    @Test
    fun `unreadable dimensions do not loop or divide by zero`() {
        assertEquals(1, BoundedBitmaps.sampleSizeFor(0, 0))
        assertEquals(1, BoundedBitmaps.sampleSizeFor(-1, 100))
    }

    @Test
    fun `an extreme image still terminates`() {
        // The loop is capped, so a pathological size returns rather than
        // spinning; the decode itself then fails and the caller falls back.
        val sample = BoundedBitmaps.sampleSizeFor(Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(sample > 1, "no downsample chosen")
    }

    private fun assertUnderBudget(
        width: Int,
        height: Int,
        sample: Int,
    ) {
        val bytes = width.toLong() / sample * (height.toLong() / sample) * 4
        assertTrue(bytes <= budget, "still ${bytes / 1024 / 1024} MB at sample $sample")
    }
}
