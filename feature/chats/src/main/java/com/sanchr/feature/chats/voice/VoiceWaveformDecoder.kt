package com.sanchr.feature.chats.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Reads a voice note's loudness out of the file itself.
 *
 * A clip carries its waveform on the wire, but not every clip has one: an
 * older build sent none, and a sender whose metering failed sends none either.
 * Android drew forty identical bars for those — a flat line presented as the
 * shape of the audio. iOS decodes the file instead, which is what this does.
 *
 * The file comes from whoever sent the message, so every loop here is bounded:
 * a clip that never signals end-of-stream, or claims to be ten minutes of
 * 48 kHz stereo, must cost a fixed amount of time and a fixed amount of memory
 * and then give up. Levels accumulate into [VoiceWaveform.DEFAULT_BINS]
 * running totals rather than a list of samples — the first version of this
 * collected every PCM frame into an `ArrayList<Int>`, which for a long clip is
 * tens of millions of boxed integers.
 */
object VoiceWaveformDecoder {
    private const val TAG = "VoiceWaveformDecoder"
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /** Longest a decode may run. A voice note is seconds long; this is slack. */
    private const val BUDGET_MS = 3_000L

    /** Consecutive empty dequeues before the decoder is declared stuck. */
    private const val MAX_IDLE_ROUNDS = 64

    /** Plausible voice-note audio. Anything else is not worth decoding blind. */
    private const val MAX_CHANNELS = 2
    private const val MIN_SAMPLE_RATE = 8_000
    private const val MAX_SAMPLE_RATE = 48_000

    /** Keyed by path and length: a cache path is reused after an evict. */
    private val cache = HashMap<String, List<Float>>()

    /**
     * Loudness buckets for [file] on the shared scale, or an empty list when
     * the audio cannot be decoded within budget — in which case the caller
     * keeps its placeholder, as iOS does.
     */
    suspend fun decode(
        file: File,
        bins: Int = VoiceWaveform.DEFAULT_BINS,
    ): List<Float> {
        val key = file.path + ":" + file.length()
        synchronized(cache) { cache[key] }?.let { return it }
        val levels = withContext(Dispatchers.Default) { runCatching { decodeBounded(file, bins) }.getOrDefault(emptyList()) }
        if (levels.isNotEmpty()) synchronized(cache) { cache[key] = levels }
        return levels
    }

    private suspend fun decodeBounded(
        file: File,
        bins: Int,
    ): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.path)
            val track = audioTrack(extractor) ?: return emptyList()
            val format = extractor.getTrackFormat(track)
            if (!isPlausibleVoice(format)) return emptyList()
            // Bins are assigned by position in the clip, so the length has to
            // be known up front; without it there is no streaming way to say
            // which bin a frame belongs to.
            val durationUs = format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION) ?: 0L
            if (durationUs <= 0) return emptyList()
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            extractor.selectTrack(track)
            codec =
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            accumulate(extractor, codec, bins, durationUs)
        } catch (e: Exception) {
            Log.w(TAG, "could not decode a waveform for ${file.name}: ${e.message}")
            emptyList()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    private fun isPlausibleVoice(format: MediaFormat): Boolean {
        val channels = format.takeIf { it.containsKey(MediaFormat.KEY_CHANNEL_COUNT) }?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
        val rate = format.takeIf { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: MIN_SAMPLE_RATE
        return channels in 1..MAX_CHANNELS && rate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE
    }

    /**
     * Decodes the track, adding each frame's level to the bin its timestamp
     * falls in.
     *
     * Fixed memory — two arrays of [bins] — and a wall-clock budget, because
     * both the file and its headers are someone else's input. A decode that
     * runs out of budget returns what it has rather than nothing, since a
     * partial waveform is still the shape of the start of the clip.
     */
    private suspend fun accumulate(
        extractor: MediaExtractor,
        codec: MediaCodec,
        bins: Int,
        durationUs: Long,
    ): List<Float> {
        val sums = DoubleArray(bins)
        val counts = IntArray(bins)
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + BUDGET_MS
        var inputDone = false
        var idleRounds = 0
        var finished = false

        // One exit condition rather than several breaks: the budget, the
        // stream ending, and a decoder that has stopped producing anything —
        // the last because a file that never reports end-of-stream would
        // otherwise spin here for the whole budget every time it was opened.
        while (!finished && idleRounds <= MAX_IDLE_ROUNDS && SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            if (!inputDone) inputDone = feed(extractor, codec)
            val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                idleRounds++
            } else {
                idleRounds = 0
                val bin = ((info.presentationTimeUs.toDouble() / durationUs) * bins).toInt().coerceIn(0, bins - 1)
                codec.getOutputBuffer(index)?.let { buffer -> addLevels(buffer, bin, sums, counts) }
                codec.releaseOutputBuffer(index, false)
                finished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }

        if (counts.all { it == 0 }) return emptyList()
        return List(bins) { i -> if (counts[i] == 0) 0f else (sums[i] / counts[i]).toFloat().coerceIn(0f, 1f) }
    }

    /** Queues one buffer. Returns whether the input is exhausted. */
    private fun feed(
        extractor: MediaExtractor,
        codec: MediaCodec,
    ): Boolean {
        val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        val buffer = codec.getInputBuffer(index)
        val read = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
        if (read < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /**
     * Adds one decoded buffer's frames to [bin].
     *
     * Each frame goes through [VoiceWaveform.level] here, the same curve the
     * recorder's samples take, so a decoded waveform and a recorded one are on
     * one scale rather than two that merely look alike.
     */
    private fun addLevels(
        buffer: ByteBuffer,
        bin: Int,
        sums: DoubleArray,
        counts: IntArray,
    ) {
        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        while (shorts.hasRemaining()) {
            sums[bin] += VoiceWaveform.level(shorts.get().toInt()).toDouble()
            counts[bin]++
        }
    }
}
