package com.sanchr.feature.chats.media

import android.view.WindowManager
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.domain.messaging.media.GallerySaver
import com.sanchr.feature.chats.MessageUiModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen photo and video viewer, opened by tapping media in a chat.
 *
 * Swipes sideways through every photo and video in the conversation, pinches
 * to zoom, plays video in place, and saves the picture on screen to the
 * device gallery — the same set of actions iOS's `MediaGalleryView` offers.
 *
 * The window is marked secure whenever the user's screenshot protection is
 * on, so opening a photo does not become the one place a chat can be captured.
 */
@Composable
fun MediaGallery(
    state: GalleryState,
    openAttachment: suspend (MessageUiModel) -> File?,
    onClose: () -> Unit,
    secure: Boolean,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        if (secure) {
            val window = (view.parent as? DialogWindowProvider)?.window
            LaunchedEffect(window) {
                window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        val pagerState = rememberPagerState(initialPage = state.initialIndex) { state.items.size }
        var chromeVisible by remember { mutableStateOf(true) }
        // Whether the page in front is zoomed in. A new page starts unzoomed.
        var zoomed by remember(pagerState.currentPage) { mutableStateOf(false) }
        var toast by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // A zoomed page pans within itself, so paging is disabled while
                // zoomed rather than fighting the drag for the same gesture.
                // (That was the intention all along; this was simply `true`,
                // so dragging across a zoomed photo turned the page instead.)
                userScrollEnabled = !zoomed,
            ) { page ->
                GalleryPage(
                    message = state.items[page],
                    openAttachment = openAttachment,
                    onSingleTap = { chromeVisible = !chromeVisible },
                    onZoomChanged = { if (page == pagerState.currentPage) zoomed = it },
                )
            }

            if (chromeVisible) {
                GalleryChrome(
                    message = state.items.getOrNull(pagerState.currentPage),
                    openAttachment = openAttachment,
                    onClose = onClose,
                    onToast = { toast = it },
                )
                if (state.items.size > 1) {
                    val scope = rememberCoroutineScope()
                    Filmstrip(
                        items = state.items,
                        currentPage = pagerState.currentPage,
                        openAttachment = openAttachment,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            toast?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(TOAST_MILLIS)
                    toast = null
                }
                Text(
                    text = message,
                    color = Color.White,
                    style = IosType.body,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = TOAST_BOTTOM_DP.dp)
                            .background(Color.Black.copy(alpha = TOAST_SCRIM))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** One photo (zoomable) or video (playing) in the pager. */
@Composable
private fun GalleryPage(
    message: MessageUiModel,
    openAttachment: suspend (MessageUiModel) -> File?,
    onSingleTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var file by remember(message.id) { mutableStateOf<File?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    // Counted up by a tap on a page that failed, which is all it takes to
    // try again; a failure here used to be final for as long as the viewer
    // stayed open.
    var attempt by remember(message.id) { mutableIntStateOf(0) }
    LaunchedEffect(message.id, attempt) {
        failed = false
        val opened = openAttachment(message)
        if (opened == null) failed = true else file = opened
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val local = file
        when {
            local != null && message.isVideoAttachment -> GalleryVideo(file = local)
            local != null ->
                ZoomableImage(
                    file = local,
                    contentDescription = message.text,
                    onSingleTap = onSingleTap,
                    onZoomChanged = onZoomChanged,
                )
            failed ->
                Text(
                    text = (if (message.isVideoAttachment) "Couldn't load this video" else "Couldn't load this photo") + "\nTap to retry",
                    color = Color.White,
                    style = IosType.body,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(onClickLabel = "Try again") { attempt++ }.padding(24.dp),
                )
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * A photo that pinches to zoom and drags to pan.
 *
 * Panning is clamped to the scaled image's own bounds, so a zoomed picture
 * cannot be dragged off screen and left as an empty black page.
 */
@Composable
private fun ZoomableImage(
    file: File,
    contentDescription: String,
    onSingleTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(file) { mutableStateOf(1f) }
    LaunchedEffect(scale > 1f) { onZoomChanged(scale > 1f) }
    var offsetX by remember(file) { mutableStateOf(0f) }
    var offsetY by remember(file) { mutableStateOf(0f) }
    var frameWidth by remember(file) { mutableStateOf(0f) }
    var frameHeight by remember(file) { mutableStateOf(0f) }

    AsyncImage(
        model = file,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                .onSizeChanged { measured ->
                    frameWidth = measured.width.toFloat()
                    frameHeight = measured.height.toFloat()
                }.pointerInput(file) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val maxX = (frameWidth * (scale - 1f)) / 2f
                        val maxY = (frameHeight * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                    }
                }.pointerInput(file) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = {
                            // Double tap toggles between fit and a fixed zoom,
                            // resetting the pan so the photo cannot come back
                            // zoomed out but off-centre.
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                            offsetX = 0f
                            offsetY = 0f
                        },
                    )
                },
    )
}

/**
 * Plays one clip, with something to control it by.
 *
 * The framework's `VideoView` does the playing: the file is already decrypted
 * in our private cache and never leaves the app, so a player library would
 * add a dependency for nothing. But it was put on screen bare — it started,
 * ran to the end and stopped, and there was no way to pause it, go back, or
 * watch it again short of closing the viewer. The controls are drawn here
 * rather than with the framework's `MediaController`, which opens a window
 * of its own that ends up behind a dialog like this one.
 */
@Composable
private fun GalleryVideo(file: File) {
    var player by remember(file) { mutableStateOf<VideoView?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var positionMs by remember(file) { mutableIntStateOf(0) }
    var durationMs by remember(file) { mutableIntStateOf(0) }
    var scrubbing by remember(file) { mutableStateOf(false) }

    // The view has no callback for "the position moved"; ask it a few times a second.
    LaunchedEffect(player) {
        val view = player ?: return@LaunchedEffect
        while (true) {
            playing = view.isPlaying
            if (!scrubbing) positionMs = view.currentPosition
            kotlinx.coroutines.delay(VIDEO_POLL_MILLIS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { prepared ->
                        prepared.isLooping = false
                        durationMs = duration.coerceAtLeast(0)
                        start()
                    }
                    // Back to the start, ready to be played again.
                    setOnCompletionListener { seekTo(0) }
                    player = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = {
                player = null
                it.stopPlayback()
            },
        )
        VideoControls(
            playing = playing,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPause = { player?.let { view -> if (view.isPlaying) view.pause() else view.start() } },
            onScrub = { target ->
                scrubbing = true
                positionMs = target
            },
            onScrubFinished = {
                player?.seekTo(positionMs)
                scrubbing = false
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = VIDEO_CONTROLS_BOTTOM_DP.dp),
        )
    }
}

@Composable
private fun VideoControls(
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onScrub: (Int) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = TOAST_SCRIM)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White,
            )
        }
        Text(text = VideoClock.format(positionMs), color = Color.White, style = IosType.captionSmall)
        Slider(
            value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(0f)),
            onValueChange = { onScrub(it.toInt()) },
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
            enabled = durationMs > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(text = VideoClock.format(durationMs), color = Color.White, style = IosType.captionSmall)
    }
}

/** Minutes and seconds for a position in a clip: 0:07, 12:40, 1:02:03. */
internal object VideoClock {
    private const val MILLIS_PER_SECOND = 1_000
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60

    fun format(millis: Int): String {
        val totalSeconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

/** Close and save, over the top of the current page. */
@Composable
private fun GalleryChrome(
    message: MessageUiModel?,
    openAttachment: suspend (MessageUiModel) -> File?,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
        if (message != null) {
            IconButton(
                onClick = {
                    scope.launch {
                        val file = openAttachment(message)
                        val saved =
                            file != null &&
                                withContext(Dispatchers.IO) {
                                    GallerySaver.save(context, file, message.attachment?.mimeType ?: "image/jpeg")
                                }
                        onToast(if (saved) "Saved to your gallery" else "Couldn't save this file")
                    }
                },
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Save to gallery", tint = Color.White)
            }
        }
    }
}

/** Thumbnails of the whole set, so a long conversation is navigable without swiping through it. */
@Composable
private fun Filmstrip(
    items: List<MessageUiModel>,
    currentPage: Int,
    openAttachment: suspend (MessageUiModel) -> File?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = TOAST_SCRIM)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(items) { index, item ->
            var file by remember(item.id) { mutableStateOf<File?>(null) }
            LaunchedEffect(item.id) { file = openAttachment(item) }
            Box(
                modifier =
                    Modifier
                        .size(THUMB_DP.dp)
                        .border(
                            width = if (index == currentPage) 2.dp else 0.dp,
                            color = if (index == currentPage) Color.White else Color.Transparent,
                        ).background(Color.DarkGray)
                        .clickable { onSelect(index) },
            ) {
                file?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val THUMB_DP = 48
private const val TOAST_MILLIS = 2_000L
private const val TOAST_BOTTOM_DP = 96
private const val TOAST_SCRIM = 0.6f
private const val VIDEO_POLL_MILLIS = 250L

/** Clear of the filmstrip, which sits along the bottom edge when there is more than one item. */
private const val VIDEO_CONTROLS_BOTTOM_DP = 72
