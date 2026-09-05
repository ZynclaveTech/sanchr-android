package com.sanchr.feature.chats.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** A finished voice note: the AAC file plus what the message carries about it. */
class VoiceClip(
    val file: File,
    val durationMs: Int,
    val waveform: List<Float>,
)

/**
 * Records a voice note the way iOS `RealAudioRecorder` does: AAC in an
 * MPEG-4 container, 44.1 kHz mono at 32 kbps (`audio/mp4`), sampling the
 * microphone level while recording for the waveform.
 */
class VoiceRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val samples = mutableListOf<Int>()

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val out = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        val r =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioChannels(1)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            samples.clear()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start recording: ${e.message}")
            r.release()
            out.delete()
            false
        }
    }

    /** Call periodically while recording (every ~100 ms) to build the waveform. */
    fun sample() {
        val r = recorder ?: return
        samples += runCatching { r.maxAmplitude }.getOrDefault(0)
    }

    fun elapsedMs(): Long = if (recorder == null) 0L else System.currentTimeMillis() - startedAt

    /** @return the clip, or null when the recording was too short or failed. */
    fun stop(): VoiceClip? {
        val r = recorder ?: return null
        val out = file
        recorder = null
        file = null
        val durationMs = (System.currentTimeMillis() - startedAt).toInt()
        val ok =
            try {
                r.stop()
                true
            } catch (e: RuntimeException) {
                // MediaRecorder.stop() throws when nothing valid was written (a sub-second tap).
                Log.w(TAG, "Recording produced no data: ${e.message}")
                false
            } finally {
                r.release()
            }
        val usable = ok && out?.isFile == true && durationMs >= MIN_DURATION_MS
        if (!usable) {
            out?.delete()
            return null
        }
        return VoiceClip(checkNotNull(out), durationMs, VoiceWaveform.bucket(samples))
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
        file = null
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 32_000
        const val MIN_DURATION_MS = 500
    }
}
