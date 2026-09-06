package com.sanchr.feature.chats.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.feature.chats.media.editor.EditedImageStore
import com.sanchr.feature.chats.media.editor.ImageEditorScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reviews several picked photos before they are sent, with one caption.
 *
 * Android could only attach one file at a time and had nowhere to caption it,
 * so sending five photos meant five trips through the system picker and no
 * way to say what they were. Mirrors iOS `MediaBatchReviewView`.
 */
@Composable
fun MediaBatchReview(
    uris: List<Uri>,
    onCancel: () -> Unit,
    onSend: (uris: List<Uri>, caption: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var selected by remember(uris) { mutableStateOf(uris) }
    var previewed by remember(uris) { mutableStateOf(uris.firstOrNull()) }
    var editing by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // The editor replaces the review screen rather than stacking on top of
    // it: two dialogs deep, the outer scrim renders over the inner content on
    // some OEM builds, and the editor wants the whole screen regardless.
    editing?.let { source ->
        Dialog(
            onDismissRequest = { editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ImageEditorScreen(
                source = source,
                onCancel = { editing = null },
                onDone = { edited ->
                    val replacement = EditedImageStore.save(context, edited)
                    if (replacement != null) {
                        val original = previewed
                        // Replaced in place so the photo keeps its position in
                        // the strip; appending would reorder what is sent.
                        selected = selected.map { if (it == original) replacement else it }
                        previewed = replacement
                    }
                    editing = null
                },
            )
        }
        return
    }

    // Removing the last one is a cancel, not an empty review screen.
    if (selected.isEmpty()) {
        onCancel()
        return
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                Text(
                    text = if (selected.size == 1) "1 photo" else "${selected.size} photos",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                previewed?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = {
                            // Off the main thread and through the bounded
                            // decoder. A photo from a recent phone is over
                            // 100 megapixels — 400 MB as ARGB — so decoding it
                            // whole, on the UI thread, froze the app and then
                            // ended it. Decoded here rather than in the editor
                            // so a file that cannot be read leaves the review
                            // screen up instead of opening an empty editor.
                            scope.launch {
                                editing =
                                    withContext(Dispatchers.IO) {
                                        BoundedBitmaps.decode(context.contentResolver, uri)
                                    }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(SanchrTheme.spacing.sm),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit photo", tint = Color.White)
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
                itemsIndexed(selected) { _, uri ->
                    val isPreviewed = uri == previewed
                    Box(modifier = Modifier.size(64.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (isPreviewed) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        } else {
                                            Modifier
                                        },
                                    ).clickable { previewed = uri },
                        )
                        IconButton(
                            onClick = {
                                selected = selected - uri
                                if (previewed == uri) previewed = selected.firstOrNull()
                            },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White)
                        }
                    }
                }
            }

            SanchrTextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = "Add a caption",
                modifier = Modifier.fillMaxWidth(),
            )
            SanchrButton(
                text = "Send",
                onClick = { onSend(selected, caption.trim().takeIf { it.isNotEmpty() }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
