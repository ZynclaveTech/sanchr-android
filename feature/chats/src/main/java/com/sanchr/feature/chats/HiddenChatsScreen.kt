package com.sanchr.feature.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * The one place hidden chats are listed, so a conversation the user hid can
 * be found again and restored. Mirrors iOS `HiddenChatsView`.
 *
 * Hiding is device-local and deletes nothing: a restored chat comes back with
 * its transcript intact.
 */
@Composable
fun HiddenChatsScreen(
    onConversationClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsListViewModel = hiltViewModel(),
) {
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<com.sanchr.core.model.Conversation?>(null) }

    val visible =
        if (query.isBlank()) {
            hidden
        } else {
            hidden.filter { it.title?.contains(query.trim(), ignoreCase = true) == true }
        }

    Scaffold(
        topBar = { SanchrTopBar(title = "Hidden", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SanchrTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search hidden chats",
                modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            )
            if (visible.isEmpty()) {
                EmptyHiddenState(hasQuery = query.isNotBlank())
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = visible, key = { it.id }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            currentUserId = "",
                            isTyping = false,
                            onClick = { onConversationClick(conversation.id) },
                            actions =
                                ConversationActions(
                                    onTogglePin = { viewModel.togglePin(conversation) },
                                    onToggleMute = { viewModel.toggleMute(conversation) },
                                    onMarkAsRead = { viewModel.markAsRead(conversation) },
                                    onArchive = null,
                                    onDelete = { deleting = conversation },
                                    onUnhide = { viewModel.setHidden(conversation, hidden = false) },
                                ),
                        )
                    }
                }
            }
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
}

@Composable
private fun EmptyHiddenState(hasQuery: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SanchrTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (hasQuery) "No hidden chat matches that." else "Nothing is hidden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
