package com.sanchr.domain.messaging.media

/**
 * Decides whether a video is worth re-encoding before it is sent, and to what.
 *
 * A port of iOS `VideoCompressionPolicy`, kept apart from the transcode itself
 * so the judgement calls — which clips to leave alone, which target to aim at —
 * are testable without running one.
 *
 * Android sent the original bytes: a minute of 4K is several hundred megabytes,
 * paid for by the sender's data plan and the recipient's, where the same clip at
 * 720p is tens. The attachment cap stopped the worst of it from crashing the
 * process; it did nothing about the cost of everything under the cap.
 */
object VideoCompressionPolicy {
    /** Long edge of the target. 720p is visibly fine on a phone. */
    const val TARGET_LONG_EDGE = 1280

    /** Long edge in Low Data Mode: roughly half the bytes of 720p, still readable. */
    const val LOW_DATA_LONG_EDGE = 960

    /**
     * Clips at or below this go as they are. Re-encoding something already
     * small wastes time and battery and can make it *bigger* — a short clip
     * already compressed once often grows on a second pass.
     */
    const val SKIP_BELOW_BYTES = 2L * 1024 * 1024

    /** A saving under a tenth is not worth the quality loss. */
    private const val WORTH_KEEPING_RATIO = 0.9

    /** How much heavier than [SKIP_BELOW_BYTES] a clip at target size may be before it is re-encoded anyway. */
    private const val SENSIBLE_ENCODING_FACTOR = 8

    sealed interface Decision {
        /** Send the original untouched. [reason] is for the log, not the user. */
        data class SendOriginal(
            val reason: String,
        ) : Decision

        /** Re-encode, capping the long edge at [longEdge]. */
        data class Compress(
            val longEdge: Int,
        ) : Decision
    }

    /**
     * @param width the video's natural width, before any rotation; only the
     *   longer and shorter edges matter, so a portrait clip is treated as its
     *   landscape equivalent.
     * @param byteCount size of the source file.
     * @param lowData the Low Data Mode switch. When on the target is
     *   [LOW_DATA_LONG_EDGE] and only clips already small *and* within it are
     *   left alone — which is the first thing on Android that switch has ever
     *   actually changed.
     */
    fun decide(
        width: Int,
        height: Int,
        byteCount: Long,
        lowData: Boolean = false,
    ): Decision {
        if (byteCount <= 0) return Decision.SendOriginal("unknown size")
        // Dimensions could not be read. Re-encoding blind risks making things
        // worse, and the file is still perfectly sendable.
        if (width <= 0 || height <= 0) return Decision.SendOriginal("unknown dimensions")

        val skipBelow = if (lowData) SKIP_BELOW_BYTES / 2 else SKIP_BELOW_BYTES
        if (byteCount <= skipBelow) return Decision.SendOriginal("already small")

        val longEdge = maxOf(width, height)
        val target = if (lowData) LOW_DATA_LONG_EDGE else TARGET_LONG_EDGE
        if (longEdge <= target && byteCount <= skipBelow * SENSIBLE_ENCODING_FACTOR) {
            // At or below the target resolution and not unreasonably heavy for
            // it — already encoded sensibly.
            return Decision.SendOriginal("already within target")
        }
        return Decision.Compress(longEdge = target)
    }

    /**
     * Whether the freshly encoded file is actually worth sending instead.
     *
     * A transcode can produce something larger than its source — a clip already
     * compressed once, or a very short one where container overhead dominates.
     * Sending that would make the feature actively harmful.
     */
    fun isWorthKeeping(
        compressedBytes: Long,
        originalBytes: Long,
    ): Boolean {
        if (compressedBytes <= 0 || originalBytes <= 0) return false
        return compressedBytes < originalBytes * WORTH_KEEPING_RATIO
    }
}
