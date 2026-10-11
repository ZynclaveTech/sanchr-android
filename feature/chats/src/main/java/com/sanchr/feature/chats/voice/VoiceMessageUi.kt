package com.sanchr.feature.chats.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sanchr.core.designsystem.theme.IosType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The mic control for the composer. Tap to start (asking for the microphone
 * permission the first time), tap again to stop — which now hands the clip to
 * a preview rather than sending it.
 *
 * Stopping used to send immediately, so a voice note could not be listened to,
 * re-recorded or thought better of: the only way out was to notice the mistake
 * before releasing the button. iOS previews every clip before it goes.
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
    var pending by remember { mutableStateOf<VoiceClip?>(null) }
    val askPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (recorder.start()) recording = true
            } else {
                // Refusing used to be met with nothing: the button simply did
                // not record, with no word as to why.
                Toast.makeText(context, "Microphone access is needed to record a voice message", Toast.LENGTH_SHORT).show()
            }
        }
    LaunchedEffect(recording) {
        while (recording) {
            recorder.sample()
            elapsedMs = recorder.elapsedMs().toInt()
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            // A clip still waiting in the preview when the screen goes is
            // deleted: it was never sent, and it is audio of the user in the
            // clear.
            pending?.file?.delete()
        }
    }

    pending?.let { clip ->
        VoiceClipPreview(
            clip = clip,
            onDiscard = {
                clip.file.delete()
                pending = null
            },
            onSend = {
                pending = null
                onClip(clip)
            },
            modifier = modifier,
        )
        return
    }

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
            Text(text = formatClock(elapsedMs), style = IosType.sectionLabel)
            Spacer(modifier = Modifier.width(4.dp))
        }
        FilledIconButton(
            onClick = {
                if (recording) {
                    recording = false
                    elapsedMs = 0
                    pending = recorder.stop()
                    // A tap too short to be a recording was dropped in silence.
                    if (pending == null) Toast.makeText(context, "Hold on a little longer to record", Toast.LENGTH_SHORT).show()
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
                contentDescription = if (recording) "Stop recording" else "Record voice message",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A recorded clip before it is sent: play it back, then send or discard.
 *
 * Replaces sending the moment the stop button is pressed, which left no way to
 * hear a voice note before the other person did.
 */
@Composable
private fun VoiceClipPreview(
    clip: VoiceClip,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        IconButton(onClick = onDiscard, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Discard recording", tint = MaterialTheme.colorScheme.error)
        }
        VoicePlayback(
            durationMs = clip.durationMs,
            waveform = clip.waveform,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            // Already on disk: this is our own recording, not a download.
            openFile = { clip.file },
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send voice message",
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
    // The clip could not be fetched. Shown, and cleared by trying again: this
    // used to reset the play button and say nothing, so a note that would not
    // load looked like a button that did not work.
    var failed by remember { mutableStateOf(false) }
    // Where to start from, when the waveform is touched before anything has
    // been loaded to seek in.
    var seekWhenReady by remember { mutableStateOf<Int?>(null) }
    val turn = remember { Any() }
    // Read out of the file when the sender attached none — an older build, or
    // one that failed to sample. Without this the bubble drew forty identical
    // bars, a flat line presented as the shape of the audio.
    // Read out of the file on first play, when the sender attached none — an
    // older build, or one that failed to sample. Until then the bubble draws
    // the flat placeholder, as iOS does.
    var decoded by remember(waveform) { mutableStateOf<List<Float>>(emptyList()) }
    val bars = waveform.ifEmpty { decoded }
    val scope = rememberCoroutineScope()

    LaunchedEffect(wantPlay) {
        if (!wantPlay) return@LaunchedEffect
        if (player == null) {
            loading = true
            failed = false
            val file = openFile()
            loading = false
            if (file == null) {
                failed = true
                wantPlay = false
                return@LaunchedEffect
            }
            player =
                preparedPlayer(file, onDuration = { totalMs = it }) {
                    playing = false
                    wantPlay = false
                    positionMs = 0
                }
            // Alongside playback, not before it: the decode is bounded but it
            // is still work, and the clip should start playing the moment the
            // user asks. On first play rather than at composition time because
            // `openFile` downloads the attachment, so decoding when a bubble
            // scrolled past would have fetched every voice note in the
            // conversation — exactly what the auto-download policy prevents.
            if (waveform.isEmpty() && decoded.isEmpty()) {
                scope.launch { decoded = VoiceWaveformDecoder.decode(file) }
            }
        }
        seekWhenReady?.let { target ->
            player?.seekTo(target)
            seekWhenReady = null
        }
        // One note at a time: whoever was playing is paused.
        VoicePlaybackTurn.take(turn) {
            player?.takeIf { it.isPlaying }?.pause()
            playing = false
            wantPlay = false
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
            VoicePlaybackTurn.release(turn)
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
            waveform = bars,
            progress = if (totalMs > 0) positionMs.toFloat() / totalMs else 0f,
            tint = tint,
            modifier =
                Modifier
                    .width(WAVEFORM_WIDTH_DP.dp)
                    .height(28.dp)
                    .seekOnTouch(totalMs) { target ->
                        positionMs = target
                        player?.seekTo(target) ?: run { seekWhenReady = target }
                    },
        )
        Spacer(modifier = Modifier.width(8.dp))
        PlaybackStatus(failed = failed, shownMs = if (playing || positionMs > 0) positionMs else totalMs, tint = tint)
    }
}

/** A player loaded and ready to start, reporting its real length and when it has finished. */
private fun preparedPlayer(
    file: File,
    onDuration: (Int) -> Unit,
    onCompleted: () -> Unit,
): MediaPlayer =
    MediaPlayer().apply {
        setDataSource(file.absolutePath)
        prepare()
        if (duration > 0) onDuration(duration)
        setOnCompletionListener { onCompleted() }
    }

/** The time, or — when the clip could not be fetched — that it could not. */
@Composable
private fun PlaybackStatus(
    failed: Boolean,
    shownMs: Int,
    tint: Color,
) {
    Text(
        text = if (failed) "Couldn't load" else formatClock(shownMs),
        style = IosType.captionSmall,
        color = if (failed) MaterialTheme.colorScheme.error else tint,
    )
}

/**
 * Touch or drag along the waveform to move through the clip. It was a picture
 * of the audio and nothing more: a two-minute note could only be heard from
 * the start.
 */
private fun Modifier.seekOnTouch(
    durationMs: Int,
    onSeek: (positionMs: Int) -> Unit,
): Modifier =
    pointerInput(durationMs) {
        detectTapGestures { onSeek(VoicePlaybackTurn.positionFor(it.x, size.width.toFloat(), durationMs)) }
    }.pointerInput(durationMs) {
        detectHorizontalDragGestures { change, _ ->
            onSeek(VoicePlaybackTurn.positionFor(change.position.x, size.width.toFloat(), durationMs))
        }
    }

@Composable
private fun WaveformBars(
    waveform: List<Float>,
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // A flat run when there is no waveform — a clip from an older build, or
    // one whose sender sent none. iOS draws the same placeholder rather than a
    // blank gap, which reads as a broken bubble rather than a quiet one. It is
    // deliberately uniform: it is not claiming to be the clip's shape.
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
