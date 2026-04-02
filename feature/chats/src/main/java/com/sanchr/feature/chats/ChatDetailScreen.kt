package com.sanchr.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent

@Composable
fun ChatDetailScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = uiState.conversation?.title ?: "Chat",
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            MessageInputBar(
                value = uiState.messageInput,
                onValueChange = viewModel::onMessageInputChanged,
                onSend = viewModel::sendMessage,
                onAttach = {
                    // TODO: Show attachment picker (photo, file, location, etc.)
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SanchrTheme.spacing.default)
                .imePadding(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.xs),
        ) {
            items(
                items = uiState.messages.reversed(),
                key = { it.id },
            ) { message ->
                val isSentByMe = message.senderId == uiState.currentUserId
                MessageBubble(
                    message = message,
                    isSentByMe = isSentByMe,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isSentByMe: Boolean,
    modifier: Modifier = Modifier,
) {
    val alignment = if (isSentByMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isSentByMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isSentByMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = if (isSentByMe) {
        SanchrShapeTokens.BubbleSent
    } else {
        SanchrShapeTokens.BubbleReceived
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = SanchrTheme.spacing.md,
                    vertical = SanchrTheme.spacing.sm,
                ),
            ) {
                when (val content = message.content) {
                    is MessageContent.Text -> {
                        Text(
                            text = content.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                    is MessageContent.Image -> {
                        // TODO: Load image with Coil
                        Text(
                            text = content.caption ?: "[Image]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                    is MessageContent.Voice -> {
                        // TODO: Voice message player UI
                        Text(
                            text = "[Voice message]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                    is MessageContent.File -> {
                        Text(
                            text = content.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                    is MessageContent.Location -> {
                        // TODO: Map thumbnail
                        Text(
                            text = content.label ?: "[Location]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                }

                // Timestamp + status
                Text(
                    text = "12:00", // TODO: Format message.timestamp
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = SanchrTheme.spacing.sm,
                    vertical = SanchrTheme.spacing.xs,
                )
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttach) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Type a message...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
