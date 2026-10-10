package com.sanchr.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.Conversation

/** Archived chats (iOS `ArchivedChatsView`): the same rows, with Unarchive in place of Archive. */
@Composable
fun ArchivedChatsScreen(
    onConversationClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsListViewModel = hiltViewModel(),
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    // Searchable, as the hidden-chats screen beside it already is and as iOS
    // is on both: an archive is where a long tail of conversations ends up,
    // which is exactly where scrolling stops being enough.
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = (uiState as? ChatsListUiState.Success)?.currentUserId.orEmpty()
    var deleting by remember { mutableStateOf<Conversation?>(null) }
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
    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SanchrTopBar(title = "Archived", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        val visible =
            if (query.isBlank()) {
                archived
            } else {
                archived.filter { it.title?.contains(query.trim(), ignoreCase = true) == true }
            }
        if (visible.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ArchivedSearchField(query = query, onQueryChange = { query = it })
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (query.isBlank()) "No archived chats" else "No matches",
                        style = IosType.body,
                        color = SanchrGray500,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ArchivedSearchField(query = query, onQueryChange = { query = it })
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = visible, key = { it.id }) { conversation ->
                        // The row's actions are a long-press menu, so Unarchive
                        // was reachable only by someone who knew to long-press: a
                        // user who did not could not leave the archive at all. iOS
                        // shows a button on every row, so this does too.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                ConversationItem(
                                    conversation = conversation,
                                    currentUserId = currentUserId,
                                    isTyping = false,
                                    onClick = { onConversationClick(conversation.id) },
                                    actions =
                                        ConversationActions(
                                            onTogglePin = { viewModel.togglePin(conversation) },
                                            onToggleMute = { viewModel.toggleMute(conversation) },
                                            onMarkAsRead = { viewModel.markAsRead(conversation) },
                                            onArchive = null,
                                            onUnarchive = { viewModel.setArchived(conversation, archived = false) },
                                            onDelete = { deleting = conversation },
                                        ),
                                )
                            }
                            TextButton(
                                onClick = { viewModel.setArchived(conversation, archived = false) },
                                modifier = Modifier.padding(end = SanchrTheme.spacing.sm),
                            ) {
                                Text(text = "Unarchive", style = IosType.captionSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The archive's own search field, matching the hidden-chats screen's. */
@Composable
private fun ArchivedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    SanchrTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search archived chats",
        modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
    )
}
