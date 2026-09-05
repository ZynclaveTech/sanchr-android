package com.sanchr.feature.chats.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
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
        var toast by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // A zoomed page pans within itself, so paging is disabled while
                // zoomed rather than fighting the drag for the same gesture.
                userScrollEnabled = true,
            ) { page ->
                GalleryPage(
                    message = state.items[page],
                    openAttachment = openAttachment,
                    onSingleTap = { chromeVisible = !chromeVisible },
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
                    style = MaterialTheme.typography.bodyMedium,
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
) {
    var file by remember(message.id) { mutableStateOf<File?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        val opened = openAttachment(message)
        if (opened == null) failed = true else file = opened
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val local = file
        when {
            local != null && message.isVideoAttachment -> GalleryVideo(file = local)
            local != null -> ZoomableImage(file = local, contentDescription = message.text, onSingleTap = onSingleTap)
            failed ->
                Text(
                    text = if (message.isVideoAttachment) "Video unavailable" else "Photo unavailable",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
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
) {
    var scale by remember(file) { mutableStateOf(1f) }
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
 * Plays one clip. The framework's `VideoView` is enough here: the file is
 * already decrypted in our private cache and never leaves the app, so a
 * player library would add a dependency for nothing.
 */
@Composable
private fun GalleryVideo(file: File) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setVideoPath(file.absolutePath)
                setOnPreparedListener { player ->
                    player.isLooping = false
                    start()
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.stopPlayback() },
    )
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
                                    saveToGallery(context, file, message.attachment?.mimeType ?: "image/jpeg", message.isVideoAttachment)
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

/**
 * Copies a decrypted attachment into the device's shared gallery.
 *
 * Uses MediaStore rather than a raw file write so it works without storage
 * permission on Android 10 and up, which is every device this app supports
 * bar the oldest. Returns whether the copy actually landed.
 */
private fun saveToGallery(
    context: Context,
    file: File,
    mimeType: String,
    isVideo: Boolean,
): Boolean =
    runCatching {
        val collection =
            if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "sanchr-${System.currentTimeMillis()}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Sanchr")
                }
            }
        val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: return@runCatching false
        true
    }.getOrDefault(false)

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val THUMB_DP = 48
private const val TOAST_MILLIS = 2_000L
private const val TOAST_BOTTOM_DP = 96
private const val TOAST_SCRIM = 0.6f
