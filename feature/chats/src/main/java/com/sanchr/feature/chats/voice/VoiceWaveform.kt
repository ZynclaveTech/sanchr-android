package com.sanchr.feature.chats.voice

/**
 * The loudness buckets sent as `audioWaveform`: `bins` values in 0..1, each
 * the mean of its slice of the recording's amplitude samples — the same
 * shape iOS `VoiceWaveformDecoder.bucketAverage` produces, so a note
 * recorded here draws the same bars on an iPhone.
 */
object VoiceWaveform {
    const val DEFAULT_BINS = 40

    fun bucket(
        samples: List<Int>,
        bins: Int = DEFAULT_BINS,
    ): List<Float> {
        if (samples.isEmpty() || bins <= 0) return emptyList()
        val peak = samples.max().coerceAtLeast(1)
        val perBin = samples.size.toDouble() / bins
        return List(minOf(bins, samples.size)) { i ->
            val from = (i * perBin).toInt()
            val to = ((i + 1) * perBin).toInt().coerceAtLeast(from + 1).coerceAtMost(samples.size)
            val mean = samples.subList(from, to).average()
            (mean / peak).toFloat().coerceIn(0f, 1f)
        }
    }
}
