package com.sanchr.feature.vault

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWarning
import com.sanchr.core.mediaviewer.DocumentPreviewScreen
import com.sanchr.core.model.VaultItem
import com.sanchr.core.model.VaultItemType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pickFile = rememberVaultFilePicker(onPicked = viewModel::addToVault)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var previewing by remember { mutableStateOf<Pair<File, VaultItem>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            handleVaultEvent(
                event = event,
                context = context,
                snackbarHostState = snackbarHostState,
                onPreview = { previewing = it },
                onShareOutside = { file, item -> shareOutside(context, file, item) },
            )
        }
    }

    // Pagination: load more when near end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 5 && uiState.hasMore && !uiState.isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SanchrTopBar(
                title = if (uiState.isSelectMode) selectionTitle(uiState.selectedIds.size) else "Vault",
                onNavigateBack = if (uiState.isSelectMode) viewModel::exitSelectMode else null,
                actions = { VaultToolbarMenu(uiState = uiState, viewModel = viewModel) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickFile.launch(arrayOf("image/*", "video/*", "audio/*", "application/*", "text/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add to Vault",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                uiState.items.isEmpty() && !uiState.isLoading -> {
                    VaultEmptyState()
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        // Info card
                        item(key = "info_card") {
                            SecureStorageInfoCard(
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.default,
                                        vertical = SanchrTheme.spacing.sm,
                                    ),
                            )
                        }

                        // Stats row
                        item(key = "stats_row") {
                            StatsRow(
                                stats = uiState.stats,
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.default,
                                        vertical = SanchrTheme.spacing.sm,
                                    ),
                            )
                        }

                        // Filter chips
                        item(key = "filter_chips") {
                            FilterChipsRow(
                                selectedFilter = uiState.filter,
                                onFilterSelected = viewModel::setFilter,
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.default,
                                        vertical = SanchrTheme.spacing.sm,
                                    ),
                            )
                        }

                        // Upload progress
                        if (uiState.isUploading) {
                            item(key = "upload_progress") {
                                LinearProgressIndicator(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = SanchrTheme.spacing.default),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Vault items
                        items(
                            items = uiState.items,
                            key = { it.id },
                        ) { item ->
                            VaultItemCard(
                                item = item,
                                isSelectMode = uiState.isSelectMode,
                                isSelected = item.id in uiState.selectedIds,
                                onOpen = {
                                    if (uiState.isSelectMode) viewModel.toggleSelection(item.id) else viewModel.openItem(item)
                                },
                                onLongPress = { if (!uiState.isSelectMode) viewModel.enterSelectMode() },
                                onDelete = { viewModel.deleteItem(item.id) },
                                onShare = { viewModel.beginShare(item) },
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.default,
                                        vertical = SanchrTheme.spacing.sm,
                                    ),
                            )
                        }

                        // Encryption notice
                        item(key = "encryption_notice") {
                            EncryptionNotice(
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.default,
                                        vertical = SanchrTheme.spacing.default,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    sharing?.let { item ->
        VaultShareSheet(
            item = item,
            conversations = conversations,
            onDismiss = viewModel::cancelShare,
            onShareInChat = { conversationId -> viewModel.shareInChat(item, conversationId) },
            onShareOutside = { viewModel.shareOutside(item) },
        )
    }

    VaultItemPreview(
        previewing = previewing,
        onDismiss = { previewing = null },
        onOpenExternally = { file, item ->
            val opened = openExternally(context, item, file)
            previewing = null
            if (!opened) {
                scope.launch { snackbarHostState.showSnackbar("No app can open ${item.mimeType}") }
            }
        },
    )
}

@Composable
private fun SecureStorageInfoCard(modifier: Modifier = Modifier) {
    SanchrCard(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SanchrTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Secure Storage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "All items are encrypted end-to-end. Only you can access your vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    stats: VaultStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(
            icon = Icons.Filled.Image,
            count = stats.photoCount,
            label = "Photos",
        )
        StatItem(
            icon = Icons.Filled.Videocam,
            count = stats.videoCount,
            label = "Videos",
        )
        StatItem(
            icon = Icons.Filled.InsertDriveFile,
            count = stats.fileCount,
            label = "Files",
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(SanchrTheme.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxs))
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: VaultFilter,
    onFilterSelected: (VaultFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
    ) {
        items(VaultFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VaultItemCard(
    item: VaultItem,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SanchrCard(modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                val thumbnail = item.thumbnailJpeg
                if (thumbnail != null) {
                    // Decrypted on this device from the metadata envelope; never a server URL.
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector =
                                when (item.type) {
                                    VaultItemType.PHOTO -> Icons.Filled.Image
                                    VaultItemType.VIDEO -> Icons.Filled.Videocam
                                    else -> Icons.Filled.Description
                                },
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(SanchrTheme.spacing.sm)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                SanchrShapeTokens.CornerSmall,
                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text =
                            when (item.type) {
                                VaultItemType.PHOTO -> "Photo"
                                VaultItemType.VIDEO -> "Video"
                                VaultItemType.AUDIO -> "Audio"
                                VaultItemType.NOTE -> "Note"
                                VaultItemType.DOCUMENT -> "File"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    text = formatFileSize(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(SanchrTheme.spacing.sm)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                SanchrShapeTokens.CornerSmall,
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(SanchrTheme.spacing.md),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxs))
                Text(
                    text = item.expiresAt?.let { "Expires ${formatExpiry(it.toEpochMilliseconds())}" } ?: "Encrypted on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.expiresAt != null) SanchrWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelectMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
            Text(
                text = "Your encrypted vault",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Store photos, videos, and files\nwith end-to-end encryption.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EncryptionNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))
        Text(
            text = "End-to-end encrypted",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

private fun formatExpiry(epochMillis: Long): String {
    val hours = ((epochMillis - System.currentTimeMillis()) / (60L * 60 * 1000)).coerceAtLeast(0)
    return if (hours >= 48) "in ${hours / 24}d" else "in ${hours}h"
}

/**
 * The system document picker (no storage permission; the app sees only the
 * chosen file). Reads bytes, name and type off the main thread and builds a
 * preview for images before handing everything to [onPicked].
 */
@Composable
private fun rememberVaultFilePicker(onPicked: (PickedFile) -> Unit): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked =
                withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val name =
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        } ?: uri.lastPathSegment ?: "file"
                    val thumbnail = if (mime.startsWith("image/")) VaultThumbnails.forImage(bytes) else null
                    PickedFile(name = name, mimeType = mime, bytes = bytes, thumbnailJpeg = thumbnail)
                }
            if (picked != null) onPicked(picked)
        }
    }
}

/**
 * One vault event, applied.
 *
 * Split out of VaultScreen, which is at detekt's complexity limit — it
 * already carries the list, selection mode, pagination and the snackbar.
 */
private suspend fun handleVaultEvent(
    event: VaultEvent,
    context: Context,
    snackbarHostState: SnackbarHostState,
    onPreview: (Pair<File, VaultItem>) -> Unit,
    onShareOutside: (File, VaultItem) -> Unit,
) {
    when (event) {
        is VaultEvent.Opened -> {
            // Decrypted into the sandbox and shown in our own viewer. Handing
            // it straight to another app is what this feature exists to
            // avoid — these are the files the user chose to put behind a lock.
            val file = writeToSandbox(context, event.item, event.bytes)
            if (file == null) {
                snackbarHostState.showSnackbar("Could not open ${event.item.name}")
            } else {
                onPreview(file to event.item)
            }
        }

        is VaultEvent.ShareOutside -> {
            // Written to the sandbox and handed out through the FileProvider.
            // Unlike sharing into a chat, this genuinely gives another app the
            // decrypted bytes — which is why the chooser says so before it
            // gets here.
            val file = writeToSandbox(context, event.item, event.bytes)
            if (file == null) {
                snackbarHostState.showSnackbar("Could not open ${event.item.name}")
            } else {
                onShareOutside(file, event.item)
            }
        }

        VaultEvent.SharedToChat -> snackbarHostState.showSnackbar("Sent")
        is VaultEvent.Error -> snackbarHostState.showSnackbar(event.message)
        VaultEvent.ItemAdded -> snackbarHostState.showSnackbar("Added to vault")
        VaultEvent.ItemDeleted -> snackbarHostState.showSnackbar("Removed from vault")
    }
}

/**
 * The vault's item viewer.
 *
 * Extracted so VaultScreen stays under the complexity limit; it is already
 * carrying the list, selection mode, pagination and the snackbar.
 */
@Composable
private fun VaultItemPreview(
    previewing: Pair<File, VaultItem>?,
    onDismiss: () -> Unit,
    onOpenExternally: (File, VaultItem) -> Unit,
) {
    previewing?.let { (file, item) ->
        DocumentPreviewScreen(
            file = file,
            fileName = item.name,
            mimeType = item.mimeType,
            onDismiss = onDismiss,
            onOpenExternally = { onOpenExternally(file, item) },
        )
    }
}

/**
 * Hands a decrypted vault item to the system share sheet.
 *
 * Separate from [openExternally], which asks one app to display the item.
 * This offers it to any app that accepts the type, which is a larger promise
 * and why the chooser states it plainly first.
 */
private fun shareOutside(
    context: Context,
    file: File,
    item: VaultItem,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType(item.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share ${item.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Writes the decrypted bytes to this app's private cache so the viewer can
 * read them. The plaintext exists on disk only in the sandbox, only while it
 * is being viewed.
 *
 * @return the file, or null when it could not be written.
 */
private suspend fun writeToSandbox(
    context: Context,
    item: VaultItem,
    bytes: ByteArray,
): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "vault").apply { mkdirs() }
            File(dir, "${item.id}-${File(item.name).name}").apply { writeBytes(bytes) }
        }.getOrNull()
    }

/**
 * Hands a vault item to another app, for the types nothing here can render.
 *
 * Kept, because refusing outright would make a vault the wrong place to
 * store a spreadsheet — but it is now a choice the user makes, with the
 * consequence spelled out, rather than what happens on every tap.
 */
private fun openExternally(
    context: Context,
    item: VaultItem,
    file: File,
): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** "3 items" / "1 item", for the title bar and the delete entry. */
private fun selectionTitle(count: Int): String = if (count == 1) "1 item" else "$count items"

/** The vault's overflow menu: Select and the sort orders, or the select-mode actions. */
@Composable
private fun VaultToolbarMenu(
    uiState: VaultUiState,
    viewModel: VaultViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Menu",
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (uiState.isSelectMode) {
                DropdownMenuItem(
                    text = { Text("Select all") },
                    onClick = {
                        menuOpen = false
                        viewModel.selectAllVisible()
                    },
                )
                if (uiState.selectedIds.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete ${selectionTitle(uiState.selectedIds.size)}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            viewModel.deleteSelected()
                        },
                    )
                }
                return@DropdownMenu
            }
            DropdownMenuItem(
                text = { Text("Select") },
                leadingIcon = { Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    viewModel.enterSelectMode()
                },
            )
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            VaultSort.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    trailingIcon = {
                        if (order == uiState.sort) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected")
                        }
                    },
                    onClick = {
                        menuOpen = false
                        viewModel.setSort(order)
                    },
                )
            }
        }
    }
}
