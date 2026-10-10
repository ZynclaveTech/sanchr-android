package com.sanchr.feature.chats.voice

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * The loudness buckets sent as `audioWaveform`: `bins` values in 0..1, each
 * the mean of its slice of the recording's samples.
 *
 * Normalised against a fixed reference, the way iOS normalises its recorder's
 * samples — `(clamp(dB, -60, 0) + 60) / 60`. This used to divide by the
 * clip's own loudest moment, which made the scale relative to the clip: every
 * note had one bar at full height, and a whisper drew the same shape as a
 * shout. The file claimed parity with iOS while producing something iOS could
 * not have produced.
 *
 * `MediaRecorder.getMaxAmplitude` reports a linear 0..32767, so it is
 * converted to dB first and then mapped by the same curve.
 */
object VoiceWaveform {
    /** Bars per clip, as iOS decodes. */
    const val DEFAULT_BINS = 64

    /** Quietest amplitude the scale distinguishes, in dB, as iOS clamps. */
    private const val FLOOR_DB = -60.0

    /** Full scale for a 16-bit sample, the reference both platforms measure against. */
    private const val FULL_SCALE = 32_767.0

    fun bucket(
        samples: List<Int>,
        bins: Int = DEFAULT_BINS,
    ): List<Float> {
        if (samples.isEmpty() || bins <= 0) return emptyList()
        val levels = samples.map(::level)
        val perBin = levels.size.toDouble() / bins
        return List(minOf(bins, levels.size)) { i ->
            val from = (i * perBin).toInt()
            val to = ((i + 1) * perBin).toInt().coerceAtLeast(from + 1).coerceAtMost(levels.size)
            levels
                .subList(from, to)
                .average()
                .toFloat()
                .coerceIn(0f, 1f)
        }
    }

    /**
     * One linear amplitude as a 0..1 level on the shared scale.
     *
     * Silence is 0 rather than `-Infinity` dB, which is what a bare log would
     * give for an amplitude of zero.
     */
    fun level(amplitude: Int): Float {
        val magnitude = abs(amplitude).toDouble()
        if (magnitude < 1.0) return 0f
        val db = 20.0 * log10(max(magnitude, 1.0) / FULL_SCALE)
        return ((db.coerceIn(FLOOR_DB, 0.0) - FLOOR_DB) / -FLOOR_DB).toFloat().coerceIn(0f, 1f)
    }
}
