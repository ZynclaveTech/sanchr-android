package com.sanchr.feature.chats.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a voice note's loudness out of the file itself.
 *
 * A clip carries its waveform on the wire, but not every clip has one: an
 * older build sent none, and a sender who failed to sample sends none either.
 * Android drew forty identical bars for those — a flat line presented as the
 * shape of the audio. iOS decodes the file instead, which is what this does.
 *
 * Decoded once per clip and cached, because decoding is not free and the
 * answer never changes for a given file.
 */
object VoiceWaveformDecoder {
    private const val TAG = "VoiceWaveformDecoder"
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /** Keyed by path and length: a cache path is reused after an evict. */
    private val cache = HashMap<String, List<Float>>()

    /**
     * Loudness buckets for [file] on the shared scale, or an empty list when
     * the audio cannot be decoded — in which case the caller keeps its
     * placeholder, which is what iOS does too.
     */
    suspend fun decode(
        file: File,
        bins: Int = VoiceWaveform.DEFAULT_BINS,
    ): List<Float> {
        val key = file.path + ":" + file.length()
        synchronized(cache) { cache[key] }?.let { return it }
        val levels = withContext(Dispatchers.Default) { runCatching { decodeBlocking(file, bins) }.getOrDefault(emptyList()) }
        if (levels.isNotEmpty()) synchronized(cache) { cache[key] = levels }
        return levels
    }

    /**
     * Decodes the audio track to PCM and buckets the absolute amplitudes.
     *
     * The samples are fed to [VoiceWaveform.bucket], so a decoded waveform and
     * a recorded one are drawn on exactly the same scale rather than two that
     * merely look similar.
     */
    private fun decodeBlocking(
        file: File,
        bins: Int,
    ): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.path)
            val track = audioTrack(extractor) ?: return emptyList()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            codec =
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            VoiceWaveform.bucket(amplitudes(extractor, codec), bins)
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

    /**
     * Absolute 16-bit amplitudes, one per frame.
     *
     * Reads every decoded buffer rather than sampling: a voice note is seconds
     * long, and the bucketing afterwards is what reduces it.
     */
    private fun amplitudes(
        extractor: MediaExtractor,
        codec: MediaCodec,
    ): List<Int> {
        val amplitudes = ArrayList<Int>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) inputDone = feed(extractor, codec)
            outputDone = drain(codec, info, amplitudes)
        }
        return amplitudes
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

    /** Reads one decoded buffer into [into]. Returns whether the stream ended. */
    private fun drain(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        into: MutableList<Int>,
    ): Boolean {
        val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        codec.getOutputBuffer(index)?.let { buffer -> readAmplitudes(buffer, into) }
        codec.releaseOutputBuffer(index, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun readAmplitudes(
        buffer: ByteBuffer,
        into: MutableList<Int>,
    ) {
        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        while (shorts.hasRemaining()) {
            val value = shorts.get().toInt()
            // `abs(Short.MIN_VALUE)` is still negative in two's complement.
            into += if (value == Short.MIN_VALUE.toInt()) -Short.MIN_VALUE.toInt() else abs(value)
        }
    }
}
