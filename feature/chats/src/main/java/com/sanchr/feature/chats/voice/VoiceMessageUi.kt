package com.sanchr.feature.chats.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The mic control for the composer. Tap to start (asking for the
 * microphone permission the first time), tap again to stop and send;
 * the close button discards. Shows elapsed time while recording.
 */
@Composable
fun VoiceRecordButton(
    enabled: Boolean,
    onClip: (VoiceClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && recorder.start()) recording = true
        }
    LaunchedEffect(recording) {
        while (recording) {
            recorder.sample()
            elapsedMs = recorder.elapsedMs().toInt()
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (recording) {
            IconButton(
                onClick = {
                    recorder.cancel()
                    recording = false
                    elapsedMs = 0
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Discard recording", tint = MaterialTheme.colorScheme.error)
            }
            Text(text = formatClock(elapsedMs), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(4.dp))
        }
        FilledIconButton(
            onClick = {
                if (recording) {
                    recording = false
                    elapsedMs = 0
                    recorder.stop()?.let(onClip)
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    if (recorder.start()) recording = true
                } else {
                    askPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (recording) "Stop and send" else "Record voice message",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Inline playback of a decrypted voice note: play/pause, the waveform the
 * sender attached (or flat bars), and elapsed / total time. The file is
 * resolved lazily through [openFile] so the download happens on first
 * play, not on scroll.
 */
@Composable
fun VoicePlayback(
    durationMs: Int,
    waveform: List<Float>,
    tint: Color,
    openFile: suspend () -> File?,
    modifier: Modifier = Modifier,
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var totalMs by remember { mutableIntStateOf(durationMs) }
    var wantPlay by remember { mutableStateOf(false) }

    LaunchedEffect(wantPlay) {
        if (!wantPlay) return@LaunchedEffect
        if (player == null) {
            loading = true
            val file = openFile()
            loading = false
            if (file == null) {
                wantPlay = false
                return@LaunchedEffect
            }
            player =
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    if (duration > 0) totalMs = duration
                    setOnCompletionListener {
                        playing = false
                        wantPlay = false
                        positionMs = 0
                    }
                }
        }
        player?.start()
        playing = true
        while (playing) {
            positionMs = player?.currentPosition ?: 0
            delay(PROGRESS_INTERVAL_MS)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(
            onClick = {
                if (playing) {
                    player?.pause()
                    playing = false
                    wantPlay = false
                } else {
                    wantPlay = true
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = tint,
                )
            }
        }
        WaveformBars(
            waveform = waveform,
            progress = if (totalMs > 0) positionMs.toFloat() / totalMs else 0f,
            tint = tint,
            modifier =
                Modifier
                    .width(WAVEFORM_WIDTH_DP.dp)
                    .height(28.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = formatClock(if (playing) positionMs else totalMs), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun WaveformBars(
    waveform: List<Float>,
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val bars = waveform.ifEmpty { List(VoiceWaveform.DEFAULT_BINS) { FLAT_BAR } }
    Canvas(modifier = modifier) {
        val stride = size.width / bars.size
        val strokeWidth = (stride * BAR_FILL).coerceAtLeast(1f)
        bars.forEachIndexed { i, level ->
            val x = i * stride + stride / 2
            val h = (size.height * level.coerceIn(MIN_BAR, 1f))
            val played = (i + 1).toFloat() / bars.size <= progress
            drawLine(
                color = if (played) tint else tint.copy(alpha = UNPLAYED_ALPHA),
                start = Offset(x, size.height / 2 - h / 2),
                end = Offset(x, size.height / 2 + h / 2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun formatClock(ms: Int): String {
    val totalSeconds = ms / MILLIS_PER_SECOND
    return String.format(Locale.US, "%d:%02d", totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
}

private const val SAMPLE_INTERVAL_MS = 100L
private const val PROGRESS_INTERVAL_MS = 200L
private const val WAVEFORM_WIDTH_DP = 140
private const val FLAT_BAR = 0.3f
private const val MIN_BAR = 0.1f
private const val BAR_FILL = 0.6f
private const val UNPLAYED_ALPHA = 0.45f
private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
