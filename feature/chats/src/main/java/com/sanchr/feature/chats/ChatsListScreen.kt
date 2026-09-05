package com.sanchr.feature.chats

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.MessageStatus

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
    val pickerState by viewModel.picker.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val hiddenChats by viewModel.hidden.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NewChatEvent.OpenConversation -> onOpenConversation(event.conversationId)
            }
        }
    }

    Scaffold(
        topBar = {
            SanchrTopBar(title = "Chats")
        },
        floatingActionButton = {
            FloatingActionButton(
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
            // --- E2EE indicator ---
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.xs,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = SanchrGray400,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "End-to-end encrypted",
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                )
            }

            // --- Search bar ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    viewModel.onSearchQueryChanged(query)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.sm,
                        ),
                placeholder = {
                    Text(
                        text = "Search conversations...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = SanchrGray400,
                    )
                },
                singleLine = true,
                shape = SanchrShapeTokens.CornerFull,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SanchrIndigo500,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
            )

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
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                            Text(
                                text = "Start a conversation to begin messaging securely.",
                                style = MaterialTheme.typography.bodyMedium,
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
                            style = MaterialTheme.typography.bodyMedium,
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
                            if (state.archivedCount > 0) {
                                item(key = "archived") { ArchivedRow(count = state.archivedCount, onClick = onOpenArchived) }
                            }
                            if (hiddenChats.isNotEmpty()) {
                                item(key = "hidden") {
                                    ShelfRow(
                                        icon = Icons.Filled.VisibilityOff,
                                        label = "Hidden",
                                        count = hiddenChats.size,
                                        onClick = onOpenHidden,
                                    )
                                }
                            }
                            items(
                                items = state.conversations,
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
                    .padding(
                        horizontal = SanchrTheme.spacing.default,
                        vertical = SanchrTheme.spacing.md,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // --- Avatar: 48dp circle, AsyncImage or initials fallback ---
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape),
            ) {
                if (conversation.avatarUrl != null) {
                    AsyncImage(
                        model = conversation.avatarUrl,
                        contentDescription = "${conversation.title} avatar",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (conversation.title ?: "?").take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))

            // --- Name + last message ---
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = conversation.title ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isTyping) {
                    Text(
                        text = "typing…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SanchrIndigo500,
                        maxLines = 1,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val last = conversation.lastMessage
                        if (last != null && last.senderId == currentUserId) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = last.status.name.lowercase(),
                                modifier = Modifier.size(14.dp),
                                tint = if (last.status == MessageStatus.READ) SanchrIndigo500 else SanchrGray400,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        val prefix = conversation.senderPrefix(currentUserId)
                        Text(
                            text = if (prefix != null) prefix + conversation.previewText() else conversation.previewText(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onSurface else SanchrGray500,
                            fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))

            // --- Timestamp + unread badge ---
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = conversation.lastMessage?.let { formatChatTimestamp(it.timestamp.toEpochMilliseconds()) }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (conversation.unreadCount > 0) {
                            SanchrIndigo500
                        } else {
                            SanchrGray400
                        },
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = SanchrIndigo500,
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
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
                .padding(horizontal = SanchrTheme.spacing.default, vertical = SanchrTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SanchrGray400)
        Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = SanchrGray400)
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
