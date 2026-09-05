package com.sanchr.feature.chats

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrCyan50
import com.sanchr.core.designsystem.theme.SanchrCyan500
import com.sanchr.core.designsystem.theme.SanchrGray100
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo900
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWarning
import com.sanchr.core.designsystem.theme.SanchrWhite
import com.sanchr.core.model.ContactCard
import com.sanchr.core.model.Conversation
import com.sanchr.core.network.link.LinkDetector
import com.sanchr.core.network.link.LinkPreview
import com.sanchr.core.network.link.LinkPreviewFetcher
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.feature.chats.emoji.EmojiPickerSheet
import com.sanchr.feature.chats.media.AttachmentPreparer
import com.sanchr.feature.chats.media.BlurHashImages
import com.sanchr.feature.chats.media.GallerySelection
import com.sanchr.feature.chats.media.GalleryState
import com.sanchr.feature.chats.media.MediaGallery
import com.sanchr.feature.chats.voice.VoiceClip
import com.sanchr.feature.chats.voice.VoicePlayback
import com.sanchr.feature.chats.voice.VoiceRecordButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onStartCall: (peerId: String, peerName: String, isVideo: Boolean) -> Unit,
    /** Opens the peer's safety number, so a key change can be reviewed where it is reported. */
    onVerifySafetyNumber: (peerId: String, peerName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Set right before launching the picker for a view-once photo; consumed by the result.
    var viewOnceNext by remember { mutableStateOf(false) }
    val pickAttachment =
        rememberAttachmentPicker(
            onPicked = { prepared ->
                val once = viewOnceNext
                viewOnceNext = false
                viewModel.sendAttachment(if (once) prepared.asViewOnce() else prepared)
            },
        )
    val pickContact = rememberContactPicker(viewModel::sendContact)
    var viewOnceOpen by remember { mutableStateOf<MessageUiModel?>(null) }
    var gallery by remember { mutableStateOf<GalleryState?>(null) }
    // Re-read on resume: a review done on the safety-number screen must clear
    // this banner when the user comes back, not leave a stale warning.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIdentityChangeState()
        onPauseOrDispose {}
    }
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf<MessageUiModel?>(null) }
    var deleting by remember { mutableStateOf<MessageUiModel?>(null) }
    val clipboard = LocalClipboardManager.current
    val actions =
        remember(viewModel, clipboard) {
            MessageActions(
                onReply = viewModel::setReply,
                onForward = { forwarding = it },
                onCopy = { clipboard.setText(AnnotatedString(it.text)) },
                onDelete = { deleting = it },
            )
        }
    val noticeContext = LocalContext.current
    uiState.notice?.let { notice ->
        LaunchedEffect(notice) {
            Toast.makeText(noticeContext, notice, Toast.LENGTH_SHORT).show()
            viewModel.dismissNotice()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusedResultId = uiState.search?.currentId
    LaunchedEffect(focusedResultId, uiState.messages.size) {
        val id = focusedResultId ?: return@LaunchedEffect
        // The list is reversed (newest at index 0), so map the transcript index across.
        val index = uiState.messages.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(uiState.messages.size - 1 - index)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val search = uiState.search
            if (search != null) {
                ChatSearchBar(
                    state = search,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    onPrevious = viewModel::previousSearchResult,
                    onNext = viewModel::nextSearchResult,
                    onClose = viewModel::closeSearch,
                )
            } else {
                ChatDetailTopBar(
                    title = uiState.conversation?.title ?: "Chat",
                    statusText = if (uiState.peerTyping) "Typing..." else uiState.peerPresence,
                    onNavigateBack = onNavigateBack,
                    canCall = uiState.directPeerId != null,
                    onVideoCall = { uiState.directPeerId?.let { onStartCall(it, uiState.conversation?.title.orEmpty(), true) } },
                    onVoiceCall = { uiState.directPeerId?.let { onStartCall(it, uiState.conversation?.title.orEmpty(), false) } },
                    onMenu = { uiState.directPeerId?.let(onNavigateToProfile) },
                    onSearch = viewModel::openSearch,
                )
            }
        },
        bottomBar = {
            Column {
                // --- Bottom action bar: Voice + Vault ---
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SanchrTheme.spacing.default, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = SanchrShapeTokens.CornerFull,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Voice",
                                modifier = Modifier.size(16.dp),
                                tint = SanchrGray400,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Voice",
                                style = MaterialTheme.typography.labelSmall,
                                color = SanchrGray400,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = SanchrShapeTokens.CornerFull,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Vault",
                                modifier = Modifier.size(16.dp),
                                tint = SanchrGray400,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Vault",
                                style = MaterialTheme.typography.labelSmall,
                                color = SanchrGray400,
                            )
                        }
                    }
                }

                // --- Message input bar ---
                MessageDialogs(
                    viewModel = viewModel,
                    forwardTargets = uiState.forwardTargets,
                    forwarding = forwarding,
                    onForwardingDone = { forwarding = null },
                    deleting = deleting,
                    onDeletingDone = { deleting = null },
                    viewOnceOpen = viewOnceOpen,
                    onViewOnceClosed = { viewOnceOpen = null },
                    gallery = gallery,
                    galleryIsSecure = uiState.screenshotProtectionEnabled,
                    onGalleryClosed = { gallery = null },
                )
                uiState.uploadProgress?.let { fraction ->
                    // Bytes actually written, so a stalled upload stops rather than sliding to full.
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = SanchrIndigo500,
                    )
                }
                uiState.replyingTo?.let { replying ->
                    ReplyBanner(
                        authorName =
                            if (replying.isFromMe) {
                                "You"
                            } else {
                                uiState.conversation
                                    ?.title
                                    .orEmpty()
                                    .ifBlank { "Contact" }
                            },
                        preview = replying.text,
                        onClear = viewModel::clearReply,
                    )
                }
                if (emojiPickerOpen) {
                    EmojiPickerSheet(
                        onDismiss = { emojiPickerOpen = false },
                        onSelect = { emoji -> viewModel.onInputTextChanged(uiState.inputText + emoji) },
                    )
                }
                MessageInputBar(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    onEmojiClick = { emojiPickerOpen = true },
                    onSend = viewModel::sendMessage,
                    onAttachFile = { pickAttachment.launch(arrayOf("image/*", "video/*", "audio/*", "application/*", "text/*")) },
                    onAttachViewOnce = {
                        viewOnceNext = true
                        pickAttachment.launch(arrayOf("image/*"))
                    },
                    onAttachContact = { pickContact.launch(Unit) },
                    onVoiceClip = { clip ->
                        scope.launch {
                            val prepared = withContext(Dispatchers.IO) { clip.toPrepared() }
                            viewModel.sendAttachment(prepared)
                        }
                    },
                    isSending = uiState.isSending,
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            // --- E2EE banner ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SanchrCyan50,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SanchrTheme.spacing.default,
                                vertical = SanchrTheme.spacing.sm,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = SanchrCyan500,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Messages are end-to-end encrypted",
                        style = MaterialTheme.typography.labelSmall,
                        color = SanchrCyan500,
                    )
                }
            }

            // --- "Today" date chip ---
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = SanchrTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = SanchrShapeTokens.CornerFull,
                    color = SanchrGray100,
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = SanchrGray400,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // --- Typing indicator ---
            if (uiState.peerTyping) {
                Text(
                    text = "typing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                    modifier =
                        Modifier.padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = 2.dp,
                        ),
                )
            }

            // --- Messages list ---
            IdentityChangeNotice(
                uiState = uiState,
                onVerifySafetyNumber = onVerifySafetyNumber,
                onAccept = viewModel::acceptIdentityChange,
            )

            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = SanchrTheme.spacing.default),
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.xs),
            ) {
                items(
                    items = uiState.messages.reversed(),
                    key = { it.id },
                ) { message ->
                    Box(
                        modifier =
                            if (message.id == focusedResultId) {
                                Modifier.background(SanchrIndigo500.copy(alpha = 0.12f), SanchrShapeTokens.CornerMedium)
                            } else {
                                Modifier
                            },
                    ) {
                        MessageRow(
                            message = message,
                            viewModel = viewModel,
                            actions = actions,
                            onOpenViewOnce = { viewOnceOpen = it },
                            onOpenMedia = { tapped -> gallery = GallerySelection.from(uiState.messages, tapped.id) },
                            loadLinkPreview = if (uiState.linkPreviewsEnabled) viewModel::linkPreview else null,
                        )
                    }
                }
            }
        }
    }
}

/** Shows the key-change warning only when there is one, keeping the screen composable flat. */
@Composable
private fun IdentityChangeNotice(
    uiState: ChatDetailUiState,
    onVerifySafetyNumber: (peerId: String, peerName: String) -> Unit,
    onAccept: () -> Unit,
) {
    val peerId = uiState.directPeerId
    if (!uiState.identityChangePending || peerId == null) return
    val name = uiState.conversation?.title ?: "This contact"
    IdentityChangeBanner(
        contactName = name,
        onVerify = { onVerifySafetyNumber(peerId, uiState.conversation?.title.orEmpty()) },
        onAccept = onAccept,
    )
}

/**
 * The peer's security code changed and nobody has reviewed it.
 *
 * Deliberately not dismissible: it is the only sign that sends are failing
 * closed, and it stays until the user either compares the new safety number or
 * explicitly accepts the change.
 */
@Composable
private fun IdentityChangeBanner(
    contactName: String,
    onVerify: () -> Unit,
    onAccept: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SanchrWarning.copy(alpha = BANNER_TINT))
                .padding(SanchrTheme.spacing.default),
        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
            Icon(imageVector = Icons.Filled.GppMaybe, contentDescription = null, tint = SanchrWarning)
            Column {
                Text(
                    text = "Security code changed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        "$contactName's security code changed. This happens when they reinstall or switch " +
                            "devices, but it can also mean someone is intercepting this chat. Messages won't " +
                            "send until you review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm)) {
            SanchrButton(text = "Verify security code", onClick = onVerify)
            TextButton(onClick = onAccept) { Text("Accept change") }
        }
    }
}

/** Picks the bubble for one transcript row. */
@Composable
private fun MessageRow(
    message: MessageUiModel,
    viewModel: ChatDetailViewModel,
    actions: MessageActions,
    onOpenViewOnce: (MessageUiModel) -> Unit,
    onOpenMedia: (MessageUiModel) -> Unit,
    loadLinkPreview: (suspend (String) -> LinkPreview?)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when {
        message.contentType == "system" -> SystemNote(message.text)

        message.isViewOnce -> ViewOnceBubble(message = message, onOpen = { onOpenViewOnce(message) })

        message.contentType == "image" ->
            ImageMessageBubble(
                message = message,
                openAttachment = viewModel::openAttachment,
                onOpen = { onOpenMedia(message) },
            )

        message.contentType == "voice" && message.attachment != null ->
            VoiceMessageBubble(
                message = message,
                openAttachment = viewModel::openAttachment,
            )

        message.contact != null -> ContactMessageBubble(message = message, card = message.contact)

        message.attachment != null ->
            MessageBubble(
                message = message,
                onToggleReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                actions = actions,
                onClick = {
                    // Video plays in our own viewer alongside the photos;
                    // everything else still opens in whatever app handles it.
                    if (message.isVideoAttachment) {
                        onOpenMedia(message)
                    } else {
                        scope.launch {
                            val file = viewModel.openAttachment(message) ?: return@launch
                            presentAttachment(context, file, message.attachment.mimeType)
                        }
                    }
                },
            )

        else ->
            MessageBubble(
                message = message,
                onToggleReaction = { emoji -> viewModel.toggleReaction(message.id, emoji) },
                actions = actions,
                loadLinkPreview = loadLinkPreview,
            )
    }
}

// --- Top app bar ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailTopBar(
    title: String,
    statusText: String?,
    onNavigateBack: () -> Unit,
    canCall: Boolean,
    onSearch: () -> Unit,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar 36dp circle
                Surface(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    statusText?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SanchrGray400,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Filled.Search, contentDescription = "Search messages")
            }
            IconButton(onClick = onVideoCall, enabled = canCall) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Video call",
                )
            }
            IconButton(onClick = onVoiceCall, enabled = canCall) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Voice call",
                )
            }
            IconButton(onClick = onMenu) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

// --- Message bubble ---
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: MessageUiModel,
    onToggleReaction: (String) -> Unit,
    actions: MessageActions,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Null when the privacy setting is off: no link is ever fetched. */
    loadLinkPreview: (suspend (String) -> LinkPreview?)? = null,
) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    var pickerOpen by remember(message.id) { mutableStateOf(false) }

    val bubbleShape =
        if (message.isFromMe) {
            RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp,
            )
        } else {
            RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            )
        }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start,
        ) {
            Box {
                ReactionPicker(
                    open = pickerOpen,
                    onDismiss = { pickerOpen = false },
                    message = message,
                    actions = actions,
                ) { emoji ->
                    pickerOpen = false
                    onToggleReaction(emoji)
                }
                Surface(
                    shape = bubbleShape,
                    color = Color.Transparent,
                    modifier =
                        Modifier
                            .widthIn(max = 280.dp)
                            .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = { pickerOpen = true }),
                ) {
                    Box(
                        modifier =
                            if (message.isFromMe) {
                                Modifier.background(
                                    brush =
                                        Brush.linearGradient(
                                            colors = listOf(SanchrIndigo500, SanchrIndigo900),
                                        ),
                                )
                            } else {
                                Modifier.background(SanchrGray100)
                            },
                    ) {
                        Column {
                            if (message.replyToId != null) {
                                QuoteCard(message.quote, textColor = if (message.isFromMe) SanchrWhite else SanchrGray900)
                            }
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (message.isFromMe) SanchrWhite else SanchrGray900,
                                modifier =
                                    Modifier.padding(
                                        horizontal = SanchrTheme.spacing.md,
                                        vertical = SanchrTheme.spacing.sm,
                                    ),
                            )
                            if (loadLinkPreview != null && message.contentType == "text") {
                                LinkDetector.firstUrl(message.text)?.let { url ->
                                    LinkPreviewCard(url = url, isFromMe = message.isFromMe, load = loadLinkPreview)
                                }
                            }
                        }
                    }
                }
            }

            ReactionChips(message.reactions, onToggleReaction)

            // --- Timestamp + read receipts ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                )

                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(message)
                }
            }
        }
    }
}

/** iOS's quick reactions, shown on long-press above the bubble. */
private val QUICK_REACTIONS = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")

/** The long-press actions, in iOS's order: the two that apply to everything, then Copy for text, then Delete last and alone. */
private class MessageActions(
    val onReply: (MessageUiModel) -> Unit,
    val onForward: (MessageUiModel) -> Unit,
    val onCopy: (MessageUiModel) -> Unit,
    val onDelete: (MessageUiModel) -> Unit,
)

@Composable
private fun ReactionPicker(
    open: Boolean,
    onDismiss: () -> Unit,
    message: MessageUiModel,
    actions: MessageActions,
    onPick: (String) -> Unit,
) {
    fun run(action: (MessageUiModel) -> Unit) {
        onDismiss()
        action(message)
    }
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Reply") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
            onClick = { run(actions.onReply) },
        )
        DropdownMenuItem(
            text = { Text("Forward") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
            onClick = { run(actions.onForward) },
        )
        if (message.contentType == "text") {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = { run(actions.onCopy) },
            )
        }
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { run(actions.onDelete) },
        )
        Row(modifier = Modifier.padding(horizontal = SanchrTheme.spacing.xs)) {
            QUICK_REACTIONS.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier =
                        Modifier
                            .clickable { onPick(emoji) }
                            .padding(horizontal = SanchrTheme.spacing.xs, vertical = SanchrTheme.spacing.xs),
                )
            }
        }
    }
}

/**
 * The quoted message inside a reply bubble, after iOS / Signal: a stripe
 * down the leading edge, a tint over the bubble's fill, the author in
 * semibold above one line of what they said. Falls back to "Replied to a
 * message" when the quoted row is not in the loaded transcript.
 */
@Composable
private fun QuoteCard(
    quote: ReplyQuote?,
    textColor: Color,
) {
    Row(
        modifier =
            Modifier
                .padding(start = 6.dp, end = 6.dp, top = 6.dp)
                .background(textColor.copy(alpha = 0.12f), SanchrShapeTokens.CornerMedium)
                .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(textColor.copy(alpha = 0.8f)),
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = quote?.authorName ?: "Reply",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
            )
            Text(
                text = quote?.preview ?: "Replied to a message",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Above the input bar while composing a reply (iOS `ChatInputBarView` reply banner). */
@Composable
private fun ReplyBanner(
    authorName: String,
    preview: String,
    onClear: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = SanchrTheme.spacing.md, vertical = SanchrTheme.spacing.xs),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .background(SanchrIndigo500, SanchrShapeTokens.CornerFull),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SanchrIndigo500,
                    maxLines = 1,
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = SanchrGray400)
            }
        }
    }
}

/** Grouped emoji counts under a bubble; the viewer's own are outlined, tapping one toggles it. */
@Composable
private fun ReactionChips(
    chips: List<ReactionChip>,
    onToggle: (String) -> Unit,
) {
    if (chips.isEmpty()) return
    Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { chip ->
            Surface(
                shape = SanchrShapeTokens.CornerFull,
                color = if (chip.mine) SanchrIndigo500.copy(alpha = 0.15f) else SanchrGray100,
                border = if (chip.mine) BorderStroke(1.dp, SanchrIndigo500) else null,
                modifier = Modifier.clickable { onToggle(chip.emoji) },
            ) {
                Text(
                    text = if (chip.count > 1) "${chip.emoji} ${chip.count}" else chip.emoji,
                    style = MaterialTheme.typography.labelMedium,
                    color = SanchrGray900,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Status indicator rendered at the trailing edge of an outbound message.
 *
 * - SENDING / SENT / DELIVERED / READ → `DoneAll` checkmark tinted per state.
 * - FAILED + [FailureKind.UNTRUSTED_IDENTITY] → warning triangle, signalling
 *   that the peer's safety number changed. No tap-to-retry: the right action
 *   is a safety-number screen (M6); retrying blindly will fail the same way.
 * - FAILED + [FailureKind.GENERIC] / null → error-outline icon.
 *
 * The `contentDescription` is filled from [MessageUiModel.failureReason]
 * when available so screen readers and tooltip-style long-press expose the
 * concrete reason without the UI needing to know the taxonomy.
 */
@Composable
private fun MessageStatusIcon(message: MessageUiModel) {
    when (message.status) {
        MessageStatus.FAILED -> {
            val (icon, tint, defaultLabel) =
                when (message.failureKind) {
                    FailureKind.UNTRUSTED_IDENTITY ->
                        Triple(
                            Icons.Filled.Warning,
                            SanchrWarning,
                            "Peer's safety number changed",
                        )
                    FailureKind.GENERIC, null ->
                        Triple(
                            Icons.Filled.ErrorOutline,
                            MaterialTheme.colorScheme.error,
                            "Failed to send",
                        )
                }
            Icon(
                imageVector = icon,
                contentDescription = message.failureReason ?: defaultLabel,
                modifier = Modifier.size(14.dp),
                tint = tint,
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription =
                    when (message.status) {
                        MessageStatus.READ -> "Read"
                        MessageStatus.DELIVERED -> "Delivered"
                        else -> "Sent"
                    },
                modifier = Modifier.size(14.dp),
                tint =
                    when (message.status) {
                        MessageStatus.READ -> SanchrIndigo500
                        MessageStatus.DELIVERED -> SanchrGray400
                        MessageStatus.SENT -> SanchrGray400
                        MessageStatus.SENDING -> SanchrGray400.copy(alpha = 0.5f)
                        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
            )
        }
    }
}

// --- Image message bubble ---
@Composable
private fun ImageMessageBubble(
    message: MessageUiModel,
    openAttachment: suspend (MessageUiModel) -> File?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 200.dp),
                shape = SanchrShapeTokens.CornerLarge,
                colors =
                    CardDefaults.cardColors(
                        containerColor = if (message.isFromMe) SanchrIndigo500 else SanchrGray100,
                    ),
            ) {
                AttachmentImage(
                    message = message,
                    openAttachment = openAttachment,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clickable(onClick = onOpen),
                )
                if (message.text.isNotEmpty() && message.text != "[Image]") {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.isFromMe) SanchrWhite else SanchrGray900,
                        modifier = Modifier.padding(SanchrTheme.spacing.sm),
                    )
                }
            }

            // Timestamp + read receipt
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                )
                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (message.status == MessageStatus.READ) SanchrIndigo500 else SanchrGray400,
                    )
                }
            }
        }
    }
}

// --- Message input bar ---
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onEmojiClick: () -> Unit,
    onAttachFile: () -> Unit,
    onAttachViewOnce: () -> Unit,
    onAttachContact: () -> Unit,
    onVoiceClip: (VoiceClip) -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(
                        horizontal = SanchrTheme.spacing.sm,
                        vertical = SanchrTheme.spacing.xs,
                    ).imePadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            // "+" attach button
            IconButton(
                onClick = onAttachFile,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Text input
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Type a message...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onEmojiClick) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEmotions,
                            contentDescription = "Emoji",
                            tint = SanchrGray400,
                        )
                    }
                },
                maxLines = 4,
                shape = SanchrShapeTokens.CornerFull,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SanchrIndigo500,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
            )

            Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))

            // Paperclip: file or contact
            var attachMenuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { attachMenuOpen = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("File") },
                        leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachFile()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("View-once photo") },
                        leadingIcon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachViewOnce()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Contact") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = {
                            attachMenuOpen = false
                            onAttachContact()
                        },
                    )
                }
            }

            if (value.isBlank()) {
                // Nothing typed: the primary action is a voice note.
                VoiceRecordButton(enabled = !isSending, onClip = onVoiceClip)
                return@Row
            }

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending,
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = SanchrIndigo500,
                        contentColor = SanchrWhite,
                        disabledContainerColor = SanchrGray400.copy(alpha = 0.3f),
                        disabledContentColor = SanchrWhite.copy(alpha = 0.5f),
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

/**
 * Downloads and decrypts the message's image on first composition, then
 * renders it from the private cache. Shows a placeholder while fetching
 * and a broken-image label if the media is gone or fails to decrypt.
 */
@Composable
private fun AttachmentImage(
    message: MessageUiModel,
    openAttachment: suspend (MessageUiModel) -> File?,
    modifier: Modifier = Modifier,
) {
    var file by remember(message.id) { mutableStateOf<File?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        val f = openAttachment(message)
        if (f == null) failed = true else file = f
    }
    val placeholder =
        remember(message.attachment?.blurHash) {
            message.attachment
                ?.blurHash
                ?.let(BlurHashImages::placeholder)
                ?.asImageBitmap()
        }
    Box(modifier = modifier.background(SanchrGray100), contentAlignment = Alignment.Center) {
        when {
            file != null ->
                AsyncImage(
                    model = file,
                    contentDescription = message.text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            failed -> Text(text = "Image unavailable", style = MaterialTheme.typography.bodySmall, color = SanchrGray400)
            else -> {
                // The BlurHash placeholder shows the picture's shape and colours
                // while the blob downloads and decrypts, as on iOS.
                if (placeholder != null) {
                    Image(
                        bitmap = placeholder,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                    )
                }
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * Picks one phone number from the device address book. `ACTION_PICK` on the
 * phone table grants read access to just the chosen row, so this works
 * without `READ_CONTACTS` having been granted at runtime.
 */
private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
private fun rememberContactPicker(onPicked: (ContactCard) -> Unit): ManagedActivityResultLauncher<Unit, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(PickPhoneNumber) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val card = withContext(Dispatchers.IO) { readPhoneRow(context, uri) }
            if (card != null) {
                onPicked(card)
            } else {
                Toast.makeText(context, "Could not read that contact", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun readPhoneRow(
    context: Context,
    uri: Uri,
): ContactCard? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getString(0).orEmpty().trim()
            val number = c.getString(1).orEmpty().trim()
            ContactCard(name = name, phoneNumber = number).takeIf { number.isNotEmpty() }
        }
    }.getOrNull()
}

// --- Contact card bubble ---
@Composable
private fun ContactMessageBubble(
    message: MessageUiModel,
    card: ContactCard,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val foreground = if (message.isFromMe) SanchrWhite else SanchrGray900

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Card(
                modifier =
                    Modifier
                        .widthIn(max = 260.dp)
                        .clickable { addToContacts(context, card) },
                shape = SanchrShapeTokens.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = if (message.isFromMe) SanchrIndigo500 else SanchrGray100),
            ) {
                Row(
                    modifier = Modifier.padding(SanchrTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Contact",
                        tint = foreground,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
                    Column {
                        Text(
                            text = card.name.ifBlank { card.phoneNumber },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = foreground,
                        )
                        if (card.name.isNotBlank()) {
                            Text(
                                text = card.phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = foreground.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                )
                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(message)
                }
            }
        }
    }
}

/** Opens the system "new contact" form pre-filled with the card. */
private fun addToContacts(
    context: Context,
    card: ContactCard,
) {
    val intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, card.name)
            putExtra(ContactsContract.Intents.Insert.PHONE, card.phoneNumber)
        }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No contacts app available", Toast.LENGTH_SHORT).show()
    }
}

/** The system document picker; reads the file off the main thread and measures images. */
@Composable
private fun rememberAttachmentPicker(onPicked: (AttachmentUploader.Prepared) -> Unit): ManagedActivityResultLauncher<Array<String>, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { AttachmentPreparer.prepare(context, uri) }
            if (prepared != null) onPicked(prepared)
        }
    }
}

/** Hands a decrypted attachment in the private cache to a viewer through the app's FileProvider. */
private fun presentAttachment(
    context: Context,
    file: File,
    mimeType: String,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open $mimeType", Toast.LENGTH_SHORT).show()
    }
}

/** A voice note: play/pause with the sender's waveform, inside the usual bubble colours. */
@Composable
private fun VoiceMessageBubble(
    message: MessageUiModel,
    openAttachment: suspend (MessageUiModel) -> File?,
    modifier: Modifier = Modifier,
) {
    val attachment = message.attachment ?: return
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Card(
                shape = SanchrShapeTokens.CornerLarge,
                colors = CardDefaults.cardColors(containerColor = if (message.isFromMe) SanchrIndigo500 else SanchrGray100),
            ) {
                VoicePlayback(
                    durationMs = attachment.audioDurationMs ?: 0,
                    waveform = attachment.audioWaveform.orEmpty(),
                    tint = if (message.isFromMe) SanchrWhite else SanchrGray900,
                    openFile = { openAttachment(message) },
                    modifier = Modifier.padding(horizontal = SanchrTheme.spacing.sm, vertical = SanchrTheme.spacing.xs),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                )
                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    MessageStatusIcon(message)
                }
            }
        }
    }
}

/** The recorded clip as the uploader wants it: `audio/mp4`, iOS's voice fields, bytes read off the main thread. */
private fun VoiceClip.toPrepared(): AttachmentUploader.Prepared {
    val bytes = file.readBytes()
    file.delete()
    return AttachmentUploader.Prepared(
        bytes = bytes,
        mimeType = "audio/mp4",
        fileName = file.name,
        durationSeconds = durationMs / MILLIS_PER_SECOND_D,
        isVoiceMessage = true,
        audioDurationMs = durationMs,
        audioWaveform = waveform,
    )
}

private const val MILLIS_PER_SECOND_D = 1000.0

/** A centred transcript notice, e.g. the "Viewed" tombstone left by view-once media. */
@Composable
private fun SystemNote(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = SanchrGray400,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

/**
 * Unopened view-once media, after iOS `ViewOnceBubble`: nothing is decoded
 * or downloaded until the recipient taps, so the transcript never shows a
 * thumbnail of something meant to be seen once.
 */
@Composable
private fun ViewOnceBubble(
    message: MessageUiModel,
    onOpen: () -> Unit,
) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Surface(
                shape = SanchrShapeTokens.CornerLarge,
                color = SanchrIndigo500.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, SanchrIndigo500.copy(alpha = 0.35f)),
                modifier = Modifier.widthIn(min = 190.dp, max = 280.dp).clickable(onClick = onOpen),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = SanchrIndigo500)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (message.isVideoAttachment) "Video" else "Photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SanchrGray900,
                        )
                        Text(text = "View once", style = MaterialTheme.typography.labelSmall, color = SanchrGray400)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open", tint = SanchrGray400)
                }
            }
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = SanchrGray400,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Full-screen, in-app viewer for view-once media. Stays inside the app (no
 * share sheet, no external viewer) and marks its window secure so it cannot
 * be captured, as iOS's gallery does; closing it consumes the message.
 */
@Composable
private fun ViewOnceViewer(
    message: MessageUiModel,
    openAttachment: suspend (MessageUiModel) -> File?,
    onClose: () -> Unit,
) {
    var file by remember(message.id) { mutableStateOf<File?>(null) }
    var failed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        val f = openAttachment(message)
        if (f == null) failed = true else file = f
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            val local = file
            when {
                local != null && message.isVideoAttachment -> ViewOnceVideo(file = local)
                local != null ->
                    AsyncImage(
                        model = local,
                        contentDescription = "View-once photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                failed -> Text(text = if (message.isVideoAttachment) "Video unavailable" else "Photo unavailable", color = SanchrWhite)
                else -> CircularProgressIndicator(color = SanchrWhite)
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = SanchrWhite)
            }
        }
    }
}

/** Pick one or more chats to forward into (iOS `MessageForwardDestinationPicker`). */
@Composable
private fun ForwardPickerDialog(
    conversations: List<Conversation>,
    onCancel: () -> Unit,
    onSend: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Forward to") },
        text = {
            if (conversations.isEmpty()) {
                Text("No chats yet")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        val checked = conversation.id in selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (checked) selected - conversation.id else selected + conversation.id }
                                    .padding(vertical = 6.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = conversation.title ?: "Unknown", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text(if (selected.size > 1) "Send to ${selected.size}" else "Send")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteMessageDialog(
    canDeleteForEveryone: Boolean,
    onCancel: () -> Unit,
    onDelete: (forEveryone: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete message?") },
        text = {
            val body =
                if (canDeleteForEveryone) {
                    "Delete for everyone removes it from the chat on all devices."
                } else {
                    "This removes the message from this device."
                }
            Text(body)
        },
        confirmButton = {
            Row {
                if (canDeleteForEveryone) {
                    TextButton(onClick = { onDelete(true) }) { Text("For everyone", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = { onDelete(false) }) { Text("For me", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** The transient dialogs a message action opens; split out to keep the screen composable readable. */
@Composable
private fun MessageDialogs(
    viewModel: ChatDetailViewModel,
    forwardTargets: List<Conversation>,
    forwarding: MessageUiModel?,
    onForwardingDone: () -> Unit,
    deleting: MessageUiModel?,
    onDeletingDone: () -> Unit,
    viewOnceOpen: MessageUiModel?,
    onViewOnceClosed: () -> Unit,
    gallery: GalleryState?,
    galleryIsSecure: Boolean,
    onGalleryClosed: () -> Unit,
) {
    forwarding?.let { message ->
        ForwardPickerDialog(
            conversations = forwardTargets,
            onCancel = onForwardingDone,
            onSend = { targets ->
                onForwardingDone()
                viewModel.forward(message, targets)
            },
        )
    }
    deleting?.let { message ->
        DeleteMessageDialog(
            canDeleteForEveryone = message.isFromMe,
            onCancel = onDeletingDone,
            onDelete = { forEveryone ->
                onDeletingDone()
                viewModel.deleteMessage(message, forEveryone)
            },
        )
    }
    viewOnceOpen?.let { message ->
        ViewOnceViewer(
            message = message,
            openAttachment = viewModel::openAttachment,
            onClose = {
                onViewOnceClosed()
                viewModel.consumeViewOnce(message)
            },
        )
    }
    gallery?.let { state ->
        MediaGallery(
            state = state,
            openAttachment = viewModel::openAttachment,
            onClose = onGalleryClosed,
            secure = galleryIsSecure,
        )
    }
}

/** Replaces the title bar while searching (iOS): the query, "n/m", previous / next, and close. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchBar(
    state: ChatSearchState,
    onQueryChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                singleLine = true,
                placeholder = { Text("Search messages...") },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
            )
        },
        actions = {
            if (state.resultIds.isNotEmpty()) {
                Text(
                    text = "${state.currentIndex + 1}/${state.resultIds.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SanchrGray400,
                )
            } else if (state.query.isNotBlank()) {
                Text(text = "0/0", style = MaterialTheme.typography.labelMedium, color = SanchrGray400)
            }
            IconButton(onClick = onPrevious, enabled = state.resultIds.size > 1) {
                Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = "Previous result")
            }
            IconButton(onClick = onNext, enabled = state.resultIds.size > 1) {
                Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = "Next result")
            }
        },
    )
}

/**
 * The card under a text bubble for its first link (iOS `LinkPreviewCard`):
 * picture when the page has one, title, and the site. Tapping opens the
 * link in the browser. Nothing is fetched unless the privacy setting is on.
 */
@Composable
private fun LinkPreviewCard(
    url: String,
    isFromMe: Boolean,
    load: suspend (String) -> LinkPreview?,
) {
    val context = LocalContext.current
    var preview by remember(url) { mutableStateOf<LinkPreview?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    LaunchedEffect(url) {
        preview = load(url)
        loading = false
    }
    val card = preview
    if (card == null && !loading) return
    val fg = if (isFromMe) SanchrWhite else SanchrGray900
    Column(
        modifier =
            Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .width(220.dp)
                .background(fg.copy(alpha = 0.1f), SanchrShapeTokens.CornerMedium)
                .clickable(enabled = card != null) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
    ) {
        card?.imageBytes?.let { bytes ->
            AsyncImage(
                model = bytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(SanchrShapeTokens.CornerMedium),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (card == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = fg)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = LinkPreviewFetcher.domainOf(url),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.7f),
                    )
                }
            } else {
                card.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(text = card.domain, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

/**
 * Plays view-once video inside the secure dialog. The framework's own
 * `VideoView` is used rather than a player library: the file is already
 * decrypted in our private cache, it never leaves the app, and adding a
 * dependency to play one clip would not earn its keep.
 */
@Composable
private fun ViewOnceVideo(file: File) {
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

private const val BANNER_TINT = 0.12f
