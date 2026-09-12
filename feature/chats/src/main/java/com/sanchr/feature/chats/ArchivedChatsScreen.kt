package com.sanchr.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray500
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
        if (archived.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "No archived chats", style = MaterialTheme.typography.bodyMedium, color = SanchrGray500)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(items = archived, key = { it.id }) { conversation ->
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
            }
        }
    }
}
