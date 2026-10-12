package com.sanchr.feature.chats

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.theme.IosSpacing
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.ConversationType
import com.sanchr.core.model.MessageStatus
import com.sanchr.feature.chats.media.CapturedPhotos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    onConversationClick: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenHidden: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // The camera writes into a file we name up front; TakePicture reports only
    // success, so the URI has to be held across the launch.
    var pendingCapture by remember { mutableStateOf<android.net.Uri?>(null) }
    var captureToSend by remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            captureToSend = if (saved) pendingCapture else null
            if (!saved) CapturedPhotos.discard(context, pendingCapture)
            pendingCapture = null
        }

    val pickerState by viewModel.picker.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val hiddenChats by viewModel.hidden.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    actionError?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.dismissActionError()
        }
    }
    deleting?.let { conversation ->
        DeleteConversationDialog(
            title = conversation.title ?: "this chat",
            onCancel = { deleting = null },
            onDelete = {
                deleting = null
                viewModel.deleteConversation(conversation)
            },
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    captureToSend?.let { uri ->
        val ready = (uiState as? ChatsListUiState.Success)?.conversations.orEmpty()
        CaptureDestinationPicker(
            conversations = ready,
            // Dismissed without choosing a chat: the photo was taken and not
            // sent, so it should stop existing rather than sit in the cache.
            onDismiss = {
                captureToSend = null
                CapturedPhotos.discard(context, uri)
            },
            onPicked = { conversationId ->
                captureToSend = null
                viewModel.sendCapturedPhoto(context, uri, conversationId)
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NewChatEvent.OpenConversation -> onOpenConversation(event.conversationId)
                is NewChatEvent.Failed -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        // Zero, because the NavHost's Scaffold has already inset the content
        // for the system bars. Two nested Scaffolds each applying them counted
        // the status bar twice, which is the gap that appeared above the
        // header — 107px of it on this handset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                // iOS fabSize. Material's default is 56, and next to a 64dp
                // iOS button the Android one reads as a smaller affordance for
                // the same action.
                modifier = Modifier.size(IosSpacing.fabSize),
                onClick = viewModel::openNewChatPicker,
                containerColor = SanchrIndigo500,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = "New conversation",
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            ChatsBrandHeader(
                onOpenArchived = onOpenArchived,
                onOpenHidden = onOpenHidden,
                onNewChat = viewModel::openNewChatPicker,
                onCapturePhoto = {
                    val uri = CapturedPhotos.newUri(context)
                    if (uri == null) {
                        Toast.makeText(context, "Could not open the camera", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingCapture = uri
                        takePicture.launch(uri)
                    }
                },
            )

            ChatsSearchField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    viewModel.onSearchQueryChanged(query)
                },
                sortOrder = sortOrder,
                onSortOrderSelected = viewModel::onSortOrderSelected,
                // iOS: screenHorizontal aside, 2 above the field.
                modifier =
                    Modifier
                        .padding(
                            horizontal = IosScreenHorizontal,
                            vertical = 0.dp,
                        ).padding(top = 2.dp),
            )

            // iOS shows this under the chips while a sync runs. Android
            // carried isSyncing all the way into the UI state and then never
            // drew it, so a sync that took a while looked like an app that had
            // stopped.
            if ((uiState as? ChatsListUiState.Success)?.isSyncing == true) {
                SyncingIndicator()
            }

            if (uiState !is ChatsListUiState.Loading) {
                ChatFilterChips(
                    selected = selectedFilter,
                    onSelect = viewModel::onFilterSelected,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }

            when (val state = uiState) {
                is ChatsListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = SanchrIndigo500)
                    }
                }

                is ChatsListUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(SanchrTheme.spacing.xxl),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = SanchrGray400,
                            )
                            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
                            Text(
                                text = "No messages yet",
                                style = IosType.cardTitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                            Text(
                                text = "Start a conversation to begin messaging securely.",
                                style = IosType.body,
                                color = SanchrGray500,
                            )
                        }
                    }
                }

                is ChatsListUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message,
                            style = IosType.body,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is ChatsListUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (state.pinned.isNotEmpty()) {
                                item(key = "pinned-heading") {
                                    ChatsSectionHeading(text = "PINNED", icon = Icons.Filled.PushPin)
                                }
                                items(
                                    items = state.pinned,
                                    key = { "pinned-" + it.id },
                                ) { conversation ->
                                    ConversationItem(
                                        conversation = conversation,
                                        currentUserId = state.currentUserId,
                                        isTyping = conversation.id in state.typingConversationIds,
                                        onClick = { onConversationClick(conversation.id) },
                                        actions =
                                            ConversationActions(
                                                onTogglePin = { viewModel.togglePin(conversation) },
                                                onToggleMute = { viewModel.toggleMute(conversation) },
                                                onMarkAsRead = { viewModel.markAsRead(conversation) },
                                                onArchive = { viewModel.setArchived(conversation, archived = true) },
                                                onDelete = { deleting = conversation },
                                                onHide = { viewModel.setHidden(conversation, hidden = true) },
                                                onUnarchive = { viewModel.setArchived(conversation, archived = false) },
                                                onUnhide = { viewModel.setHidden(conversation, hidden = false) },
                                            ),
                                    )
                                }
                                item(key = "all-heading") { ChatsSectionHeading(text = "ALL CHATS") }
                            }
                            items(
                                items = state.unpinned,
                                key = { it.id },
                            ) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    currentUserId = state.currentUserId,
                                    isTyping = conversation.id in state.typingConversationIds,
                                    onClick = { onConversationClick(conversation.id) },
                                    actions =
                                        ConversationActions(
                                            onTogglePin = { viewModel.togglePin(conversation) },
                                            onToggleMute = { viewModel.toggleMute(conversation) },
                                            onMarkAsRead = { viewModel.markAsRead(conversation) },
                                            onArchive = { viewModel.setArchived(conversation, archived = true) },
                                            onDelete = { deleting = conversation },
                                            onHide = { viewModel.setHidden(conversation, hidden = true) },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pickerState.isOpen) {
            NewChatContactPickerSheet(
                state = pickerState,
                onSearchQueryChanged = viewModel::onPickerSearchQueryChanged,
                onContactSelected = viewModel::onPickerContactSelected,
                onDismiss = viewModel::closeNewChatPicker,
            )
        }
    }
}

/** What a long-press on a row can do (iOS's swipe actions). Null entries are hidden. */
class ConversationActions(
    val onTogglePin: () -> Unit,
    val onToggleMute: () -> Unit,
    val onMarkAsRead: () -> Unit,
    val onArchive: (() -> Unit)?,
    val onDelete: () -> Unit,
    val onUnarchive: (() -> Unit)? = null,
    val onHide: (() -> Unit)? = null,
    val onUnhide: (() -> Unit)? = null,
)

/** iOS `SanchrSpacing.chatRowVPadding`. */
private val IosRowVPadding = IosSpacing.chatRowVPadding

/** The gap between avatar and text, iOS `HStack(spacing: 12)`. */
private val IosRowGap = IosSpacing.avatarContentGap

/** iOS `SanchrSpacing.namePreviewGap` (xxs). */
private val IosNamePreviewGap = IosSpacing.namePreviewGap

/** iOS `SanchrSpacing.chatAvatarSize`. Android drew 48. */
private val IosAvatarSize = IosSpacing.chatAvatarSize

/** iOS `SanchrSpacing.statusIndicatorSize` / `statusIndicatorBorder`. */
private val IosStatusDot = IosSpacing.statusIndicatorSize
private val IosStatusDotBorder = IosSpacing.statusIndicatorBorder

/** iOS `SanchrSpacing.unreadBadgeSize`, as a minimum width rather than a fixed one. */
private val IosBadgeMinWidth = IosSpacing.unreadBadgeSize

// iOS type scale: sm 16, xs 14, xxs 12.
private val IosNameSize = 16.sp
private val IosPreviewSize = 14.sp
private val IosTimestampSize = 12.sp
private val IosBadgeSize = 12.sp

/**
 * The row's avatar: 56dp, ringed, with a presence dot.
 *
 * The ring is what separates a dark avatar from a dark page; without it a
 * photo with dark edges bleeds into the background and the row loses its
 * left margin. The dot sits bottom-trailing with its own border so it reads
 * against the photo rather than melting into it.
 */
@Composable
private fun ConversationAvatar(conversation: Conversation) {
    val ringColour = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.size(IosAvatarSize)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(2.dp, ringColour, CircleShape),
        ) {
            if (conversation.avatarUrl != null) {
                AsyncImage(
                    model = conversation.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            // iOS: primary at 14%, with the initial in primary.
                            .background(SanchrIndigo500.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (conversation.title ?: "?").take(1).uppercase(),
                        style = IosType.cardTitle,
                        color = SanchrIndigo500,
                    )
                }
            }
        }

        // Presence, which the Android row did not show at all: the online dot
        // is how the list answers "can I reach them now" without opening a chat.
        val peerOnline =
            conversation.type == ConversationType.DIRECT &&
                conversation.participants.any { it.isOnline }
        if (peerOnline) {
            Box(
                modifier =
                    Modifier
                        .size(IosStatusDot)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(ringColour)
                        .padding(IosStatusDotBorder)
                        .clip(CircleShape)
                        .background(SanchrSuccess),
            )
        }
    }
}

/**
 * The tick beside an outgoing preview.
 *
 * Android drew the same double tick for every state, so "sending" and
 * "delivered" were indistinguishable and a failed send looked delivered.
 */
@Composable
private fun DeliveryStatusIcon(status: MessageStatus) {
    val tint =
        when (status) {
            MessageStatus.READ -> SanchrSuccess
            MessageStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> SanchrGray400
        }
    val icon =
        when (status) {
            MessageStatus.SENDING -> Icons.Filled.Schedule
            MessageStatus.SENT -> Icons.Filled.Check
            MessageStatus.FAILED -> Icons.Filled.ErrorOutline
            else -> Icons.Filled.DoneAll
        }
    Icon(
        imageVector = icon,
        contentDescription = status.name.lowercase(),
        tint = tint,
        modifier = Modifier.size(14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationItem(
    conversation: Conversation,
    currentUserId: String,
    isTyping: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ConversationActions? = null,
) {
    var menuOpen by remember(conversation.id) { mutableStateOf(false) }
    Box {
        if (actions != null) {
            ConversationMenu(conversation = conversation, actions = actions, open = menuOpen, onDismiss = { menuOpen = false })
        }
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = { if (actions != null) menuOpen = true })
                    .padding(horizontal = IosScreenHorizontal, vertical = IosRowVPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IosRowGap),
        ) {
            ConversationAvatar(conversation = conversation)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(IosNamePreviewGap),
            ) {
                // Name and timestamp share a row, as on iOS. Android had the
                // timestamp in a trailing column of its own, which pushed it
                // away from the name it belongs to and left the badge floating
                // under it.
                //
                // The text takes all the width that is left, which is what
                // puts the time and the badge against the trailing edge. It
                // used to share that width with a spacer of equal weight: each
                // was offered half, the text used only what it needed, and
                // whatever it left over went unused — so the time stopped
                // short of the edge and the badge sat in the middle of the row.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = conversation.title ?: "Unknown",
                        style = IosType.conversationName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    conversation.lastMessage?.let { last ->
                        Text(
                            text = formatChatTimestamp(last.timestamp.toEpochMilliseconds()),
                            style = IosType.chatTimestamp,
                            // Plain secondary whether or not there is anything
                            // unread: iOS does not colour the time, and a blue
                            // timestamp competes with the badge that is already
                            // saying the same thing.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isTyping) {
                        Text(
                            text = "typing…",
                            style = IosType.conversationPreview,
                            color = SanchrIndigo500,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (conversation.draftPreview() != null) {
                        // A draft outranks the last message: it is what the
                        // user left unfinished. It does not outrank "typing…",
                        // which is about the other person and is live.
                        Text(
                            text =
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)) {
                                        append("Draft: ")
                                    }
                                    append(conversation.draftPreview().orEmpty())
                                },
                            style = IosType.conversationPreview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        val last = conversation.lastMessage
                        if (last != null && last.senderId == currentUserId) {
                            DeliveryStatusIcon(status = last.status)
                        }
                        val prefix = conversation.senderPrefix(currentUserId)
                        Text(
                            text = if (prefix != null) prefix + conversation.previewText() else conversation.previewText(),
                            style =
                                if (conversation.unreadCount > 0) {
                                    IosType.conversationPreviewBold
                                } else {
                                    IosType.conversationPreview
                                },
                            color =
                                if (conversation.unreadCount > 0) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (conversation.isMuted) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            tint = SanchrGray400,
                            modifier = Modifier.size(10.dp),
                        )
                    }

                    if (conversation.unreadCount > 0) {
                        // A capsule with a minimum width, not a fixed circle:
                        // a three-digit count clipped inside the 22dp circle
                        // Android drew before.
                        Box(
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = IosBadgeMinWidth)
                                    .clip(SanchrShapeTokens.CornerFull)
                                    .background(SanchrIndigo500)
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = conversation.unreadCount.toString(),
                                style = IosType.unreadBadge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationMenu(
    conversation: Conversation,
    actions: ConversationActions,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    fun run(action: () -> Unit) {
        onDismiss()
        action()
    }
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
            leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
            onClick = { run(actions.onTogglePin) },
        )
        DropdownMenuItem(
            text = { Text(if (conversation.isMuted) "Unmute" else "Mute") },
            leadingIcon = {
                Icon(
                    if (conversation.isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                    contentDescription = null,
                )
            },
            onClick = { run(actions.onToggleMute) },
        )
        if (conversation.unreadCount > 0) {
            DropdownMenuItem(
                text = { Text("Mark as read") },
                leadingIcon = { Icon(Icons.Filled.Drafts, contentDescription = null) },
                onClick = { run(actions.onMarkAsRead) },
            )
        }
        actions.onArchive?.let { archive ->
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                onClick = { run(archive) },
            )
        }
        actions.onUnarchive?.let { unarchive ->
            DropdownMenuItem(
                text = { Text("Unarchive") },
                leadingIcon = { Icon(Icons.Filled.Unarchive, contentDescription = null) },
                onClick = { run(unarchive) },
            )
        }
        actions.onHide?.let { hide ->
            DropdownMenuItem(
                text = { Text("Hide") },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                onClick = { run(hide) },
            )
        }
        actions.onUnhide?.let { unhide ->
            DropdownMenuItem(
                text = { Text("Unhide") },
                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                onClick = { run(unhide) },
            )
        }
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { run(actions.onDelete) },
        )
    }
}

/** iOS `SanchrExportMetrics.screenHorizontal`. Android's `default` is 16. */
private val IosScreenHorizontal = IosSpacing.screenHorizontal

/** iOS `SanchrExportMetrics.rootTop`. */
private val IosRootTop = IosSpacing.rootTop

/** iOS `SanchrExportMetrics.iconButtonSize`. */
private val IosIconButtonSize = IosSpacing.iconButtonSize

/** iOS `SanchrSpacing.searchBarHeight` / `searchIconSize`. */
private val IosSearchHeight = IosSpacing.searchBarHeight
private val IosSearchIconSize = IosSpacing.searchIconSize

/** iOS `chatListTitle` is xl (24) bold; search text is xs (14) medium. */
private val IosBrandTitleSize = 24.sp
private val IosSearchTextSize = 14.sp

/** iOS `SanchrSpacing.filterTabHeight`. */
private val IosChipHeight = IosSpacing.filterTabHeight

/** iOS `SanchrSpacing.filterTabHPadding`. */
private val IosChipHPadding = IosSpacing.filterTabHPadding

/** The brand mark in iOS `SanchrBrandHeader`, and the gap beside it. */
private val IosBrandMark = IosSpacing.iconButtonSize
private val IosBrandGap = IosSpacing.sm

/**
 * Where a photo taken from the chats list should go.
 *
 * iOS shows the same thing after a home-screen capture: the photo exists
 * before a conversation has been chosen, so the picker is the step between.
 * Dismissing sends nothing and leaves the file in the cache for the system to
 * reclaim.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureDestinationPicker(
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = SanchrTheme.spacing.default)) {
            Text(
                text = "Send photo to",
                style = IosType.cardTitle,
                modifier = Modifier.padding(horizontal = IosScreenHorizontal, vertical = SanchrTheme.spacing.sm),
            )
            if (conversations.isEmpty()) {
                Text(
                    text = "No conversations yet.",
                    style = IosType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = IosScreenHorizontal),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(items = conversations, key = { it.id }) { conversation ->
                        ListItem(
                            headlineContent = { Text(conversation.title ?: "Conversation") },
                            modifier = Modifier.clickable { onPicked(conversation.id) },
                        )
                    }
                }
            }
        }
    }
}

/** "Syncing…", as iOS shows it while the conversation list is catching up. */
@Composable
private fun SyncingIndicator() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = SanchrIndigo500,
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(IosSpacing.xs))
        Text(
            text = "Syncing...",
            style = IosType.captionSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The list's header: the app's name and an overflow menu.
 *
 * Mirrors iOS `SanchrBrandHeader` on this screen — the title is the product,
 * not the tab. "Chats" restated the label already lit in the bottom bar and
 * spent the most prominent line on the screen saying nothing.
 *
 * Archived and Hidden live here rather than as rows in the list, as on iOS:
 * as rows they sat above the user's actual conversations, so the first thing
 * on the screen was two shelves of chats they had deliberately put away.
 */
@Composable
private fun ChatsBrandHeader(
    onOpenArchived: () -> Unit,
    onOpenHidden: () -> Unit,
    onNewChat: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = IosScreenHorizontal,
                    end = IosScreenHorizontal,
                    // iOS SanchrBrandHeader: rootTop above, 12 below. A
                    // TopAppBar was 64dp of its own plus a second copy of the
                    // status-bar inset, which is where the gap came from.
                    top = IosRootTop,
                    bottom = 12.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IosBrandGap),
    ) {
        Image(
            painter = painterResource(id = com.sanchr.core.designsystem.R.drawable.sanchr_logo),
            contentDescription = null,
            modifier = Modifier.size(IosBrandMark),
        )
        Text(
            text = "Sanchr",
            fontSize = IosBrandTitleSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onCapturePhoto, modifier = Modifier.size(IosIconButtonSize)) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = "Take a photo")
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(IosIconButtonSize),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("New chat") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onNewChat()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Archived chats") },
                    leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenArchived()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Hidden chats") },
                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenHidden()
                    },
                )
            }
        }
    }
}

/**
 * The search field, as iOS draws it: a capsule, not an outlined box.
 *
 * 44 tall with 16 of padding and a 12 gap, which is what makes it read as a
 * single control rather than a form input. Material's OutlinedTextField
 * brought its own border, label slot and 56dp minimum, none of which iOS has.
 */
@Composable
private fun ChatsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    sortOrder: ChatSortOrder,
    onSortOrderSelected: (ChatSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IosSearchHeight)
                .clip(SanchrShapeTokens.CornerFull)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = SanchrGray400,
            modifier = Modifier.size(IosSearchIconSize),
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        fontSize = IosSearchTextSize,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(SanchrIndigo500),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = "Search chats...",
                    style = IosType.searchPlaceholder,
                    color = SanchrGray400,
                )
            }
        }
        // Empty means sort, typing means clear. iOS notes that a single
        // button which always cleared while showing a sort icon advertised a
        // control that did nothing.
        if (value.isEmpty()) {
            Box {
                IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Sort chats, currently " + sortOrder.label,
                        // Tinted only when the order is not the default, so the
                        // icon says at a glance whether anything is reordered.
                        tint = if (sortOrder == ChatSortOrder.RECENT) SanchrGray400 else SanchrIndigo500,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    ChatSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            trailingIcon = {
                                if (order == sortOrder) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = SanchrIndigo500)
                                }
                            },
                            onClick = {
                                sortMenuOpen = false
                                onSortOrderSelected(order)
                            },
                        )
                    }
                }
            }
        } else {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "Clear search",
                    tint = SanchrGray400,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The filter chips, the same three iOS shows.
 *
 * Horizontally scrollable even at three chips: the row is the same component
 * whatever it holds, and a fixed row that starts clipping the day a fourth
 * filter arrives is a worse default than one that scrolls and never does.
 */
@Composable
private fun ChatFilterChips(
    selected: ChatFilter,
    onSelect: (ChatFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = IosScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
    ) {
        ChatFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier =
                    Modifier
                        .height(IosChipHeight)
                        .clip(SanchrShapeTokens.CornerFull)
                        .then(
                            // Selected is a solid primary capsule, not a tinted
                            // one: iOS found a tint over the surface read as
                            // washed out, with the background showing through
                            // as a second shape behind the pill.
                            if (isSelected) {
                                Modifier.background(SanchrIndigo500)
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            },
                        ).clickable { onSelect(filter) }
                        .padding(horizontal = IosChipHPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.label,
                    style = if (isSelected) IosType.filterTabActive else IosType.filterTab,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A section heading over a run of rows: "PINNED", "ALL CHATS".
 *
 * iOS sets these apart with letterspaced small caps; the nearest thing here is
 * labelMedium with tracking. Without them the pinned chats merge into the rest
 * and pinning stops looking like it did anything.
 */
@Composable
private fun ChatsSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = IosScreenHorizontal,
                    end = IosScreenHorizontal,
                    // iOS SanchrSectionEyebrow: 8 above, 4 below.
                    top = 8.dp,
                    bottom = 4.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = SanchrGray400,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = text,
            style = IosType.sectionLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ArchivedRow(
    count: Int,
    onClick: () -> Unit,
) = ShelfRow(icon = Icons.Filled.Archive, label = "Archived", count = count, onClick = onClick)

/** A row above the chats that opens a shelf holding [count] conversations. */
@Composable
private fun ShelfRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = IosSpacing.screenHorizontal, vertical = SanchrTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SanchrGray400)
        Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
        Text(text = label, style = IosType.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = count.toString(), style = IosType.sectionLabel, color = SanchrGray400)
    }
}

@Composable
internal fun DeleteConversationDialog(
    title: String,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete conversation?") },
        text = { Text("This deletes your copy of the chat with $title on this device.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
