package com.sanchr.feature.chats.shared

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrGray100
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.mediaviewer.DocumentPreviewScreen
import com.sanchr.core.model.MediaAttachment
import com.sanchr.feature.chats.MessageUiModel
import com.sanchr.feature.chats.media.GallerySelection
import com.sanchr.feature.chats.media.GalleryState
import com.sanchr.feature.chats.media.MediaGallery
import java.io.File
import kotlinx.coroutines.launch

/**
 * Media, Links and Docs for one conversation, as iOS's `SharedContentView`.
 *
 * Android had no way to find a photo from three weeks ago except scrolling the
 * transcript to it.
 */
@Composable
fun SharedContentScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedContentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The browser opens media and files itself, with the viewers the chat uses:
    // it already holds the list the gallery wants, so handing the ids back to
    // the transcript would mean teaching that screen to be opened at a message.
    var gallery by remember { mutableStateOf<GalleryState?>(null) }
    var document by remember { mutableStateOf<Pair<File, MediaAttachment>?>(null) }

    gallery?.let { state ->
        MediaGallery(
            state = state,
            openAttachment = viewModel::openAttachment,
            onClose = { gallery = null },
            // The same screenshot setting the transcript's viewer honours;
            // opening a photo from here must not be the one window a capture
            // can still catch.
            secure = uiState.screenshotProtectionEnabled,
        )
    }
    document?.let { (file, attachment) ->
        DocumentPreviewScreen(
            file = file,
            fileName = attachment.filename,
            mimeType = attachment.mimeType,
            onDismiss = { document = null },
            onOpenExternally = { openExternally(context, file, attachment.mimeType) },
        )
    }

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system bars;
        // applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SanchrTopBar(title = "Media, links and docs", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = uiState.tab.ordinal) {
                SharedContent.Tab.entries.forEach { tab ->
                    Tab(
                        selected = tab == uiState.tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(text = tab.title, style = IosType.filterTab) },
                    )
                }
            }
            when {
                uiState.isLoading -> Unit
                uiState.buckets.isEmpty(uiState.tab) -> EmptyBucket(uiState.tab)
                else ->
                    when (uiState.tab) {
                        SharedContent.Tab.MEDIA ->
                            MediaGridTab(
                                media = uiState.buckets.media,
                                thumbnailFor = viewModel::cachedThumbnail,
                                onOpenMedia = { tapped ->
                                    gallery = GallerySelection.from(uiState.buckets.media, tapped.id)
                                },
                            )
                        SharedContent.Tab.LINKS -> LinksTab(uiState.buckets.links) { url -> openLink(context, url) }
                        SharedContent.Tab.DOCS ->
                            DocsTab(uiState.buckets.docs) { doc ->
                                scope.launch {
                                    val file = viewModel.openAttachment(doc)
                                    val attachment = doc.attachment
                                    if (file != null && attachment != null) {
                                        document = file to attachment
                                    } else {
                                        Toast.makeText(context, "That file couldn't be opened.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@Composable
private fun EmptyBucket(tab: SharedContent.Tab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text =
                when (tab) {
                    SharedContent.Tab.MEDIA -> "No photos or videos in this chat"
                    SharedContent.Tab.LINKS -> "No links in this chat"
                    SharedContent.Tab.DOCS -> "No files in this chat"
                },
            style = IosType.body,
            color = SanchrGray500,
        )
    }
}

@Composable
private fun MediaGridTab(
    media: List<MessageUiModel>,
    /** A tile's file when it is already on disk; null never downloads one. */
    thumbnailFor: (MessageUiModel) -> File?,
    onOpenMedia: (MessageUiModel) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(MEDIA_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize().padding(2.dp),
    ) {
        items(items = media, key = { it.id }) { item ->
            Box(
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .clip(SanchrShapeTokens.CornerSmall)
                        .background(SanchrGray100)
                        .clickable { onOpenMedia(item) },
            ) {
                // Only what is already decrypted on disk. A grid over a whole
                // conversation would otherwise download every attachment in it
                // the moment this opened, which is what the auto-download
                // policy exists to prevent; the rest show a placeholder until
                // tapped, and the gallery then fetches that one.
                val file = remember(item.id) { thumbnailFor(item) }
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = item.text.ifBlank { "Shared media" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = item.text.ifBlank { "Shared media" },
                        tint = SanchrGray400,
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinksTab(
    links: List<SharedContent.Link>,
    onOpen: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = links, key = { it.messageId }) { link ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(link.url) }
                        .padding(SanchrTheme.spacing.default),
            ) {
                Icon(imageVector = Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = link.displayDomain, style = IosType.bodyBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = link.url,
                        style = IosType.captionSmall,
                        color = SanchrGray400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocsTab(
    docs: List<MessageUiModel>,
    onOpen: (MessageUiModel) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = docs, key = { it.id }) { doc ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(doc) }
                        .padding(SanchrTheme.spacing.default),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = doc.attachment?.filename ?: "File",
                    style = IosType.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Hands a decrypted file to another app, through the app's FileProvider. */
private fun openExternally(
    context: android.content.Context,
    file: File,
    mimeType: String,
) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app can open that file", Toast.LENGTH_SHORT).show() }
}

/** Hands a link to the browser, and says so when there is nothing to open it. */
private fun openLink(
    context: android.content.Context,
    url: String,
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "No app can open that link", Toast.LENGTH_SHORT).show() }
}

private const val MEDIA_COLUMNS = 3
