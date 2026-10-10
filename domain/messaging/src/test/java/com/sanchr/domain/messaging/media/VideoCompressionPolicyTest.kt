package com.sanchr.domain.messaging.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Which clips are worth re-encoding before sending, and to what.
 *
 * Android sent the file the camera produced: a minute of 4K is several hundred
 * megabytes, charged to the sender's data plan and the recipient's. iOS has
 * capped the long edge at 720p — 540p in Low Data Mode — on every send, and
 * this is that rule.
 */
class VideoCompressionPolicyTest {
    private val fourK = 3840 to 2160
    private val sevenTwentyP = 1280 to 720

    private fun decide(
        size: Pair<Int, Int>,
        bytes: Long,
        lowData: Boolean = false,
    ) = VideoCompressionPolicy.decide(size.first, size.second, bytes, lowData)

    @Test
    fun `a 4K clip is re-encoded to 720p`() {
        val decision = decide(fourK, bytes = 300L * 1024 * 1024)

        assertIs<VideoCompressionPolicy.Decision.Compress>(decision)
        assertEquals(VideoCompressionPolicy.TARGET_LONG_EDGE, decision.longEdge)
    }

    @Test
    fun `Low Data Mode aims lower, which is the first thing that switch changes on Android`() {
        val decision = decide(fourK, bytes = 300L * 1024 * 1024, lowData = true)

        assertIs<VideoCompressionPolicy.Decision.Compress>(decision)
        assertEquals(VideoCompressionPolicy.LOW_DATA_LONG_EDGE, decision.longEdge)
    }

    @Test
    fun `a clip already small is left alone, because a second pass can make it bigger`() {
        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(fourK, bytes = 1024 * 1024))
    }

    @Test
    fun `Low Data Mode halves the leave-alone threshold`() {
        val justOverHalf = VideoCompressionPolicy.SKIP_BELOW_BYTES / 2 + 1

        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(fourK, justOverHalf))
        assertIs<VideoCompressionPolicy.Decision.Compress>(decide(fourK, justOverHalf, lowData = true))
    }

    @Test
    fun `a clip already at the target and sensibly encoded is left alone`() {
        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(sevenTwentyP, bytes = 8L * 1024 * 1024))
    }

    @Test
    fun `a clip at the target but unreasonably heavy is re-encoded anyway`() {
        assertIs<VideoCompressionPolicy.Decision.Compress>(decide(sevenTwentyP, bytes = 90L * 1024 * 1024))
    }

    @Test
    fun `a portrait clip is judged on its long edge, like its landscape twin`() {
        val portrait = decide(2160 to 3840, bytes = 300L * 1024 * 1024)
        val landscape = decide(fourK, bytes = 300L * 1024 * 1024)

        assertEquals(landscape, portrait)
    }

    @Test
    fun `unreadable dimensions or size send the original rather than re-encoding blind`() {
        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(0 to 0, bytes = 300L * 1024 * 1024))
        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(fourK, bytes = 0))
        assertIs<VideoCompressionPolicy.Decision.SendOriginal>(decide(fourK, bytes = -1))
    }

    @Test
    fun `a result that saved little is not worth keeping`() {
        // Container overhead can make a short clip grow on a second pass.
        assertFalse(VideoCompressionPolicy.isWorthKeeping(compressedBytes = 110, originalBytes = 100))
        assertFalse(VideoCompressionPolicy.isWorthKeeping(compressedBytes = 95, originalBytes = 100))
        assertTrue(VideoCompressionPolicy.isWorthKeeping(compressedBytes = 40, originalBytes = 100))
    }

    @Test
    fun `nonsense sizes are never worth keeping`() {
        assertFalse(VideoCompressionPolicy.isWorthKeeping(compressedBytes = 0, originalBytes = 100))
        assertFalse(VideoCompressionPolicy.isWorthKeeping(compressedBytes = 40, originalBytes = 0))
    }
}
