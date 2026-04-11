package com.sanchr.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.theme.SanchrCyan50
import com.sanchr.core.designsystem.theme.SanchrCyan500
import com.sanchr.core.designsystem.theme.SanchrGray100
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo900
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
            ChatDetailTopBar(
                title = uiState.conversation?.title ?: "Chat",
                statusText = if (uiState.peerTyping) {
                    "Typing..."
                } else {
                    uiState.peerPresenceText
                },
                isOnline = uiState.isPeerOnline,
                onNavigateBack = onNavigateBack,
                onVideoCall = { /* TODO */ },
                onVoiceCall = { /* TODO */ },
                onMenu = { /* TODO */ },
            )
        },
        bottomBar = {
            Column {
                // --- Bottom action bar: Voice + Vault ---
                Row(
                    modifier = Modifier
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
                MessageInputBar(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    onSend = viewModel::sendMessage,
                    onAttach = { /* TODO: attachment picker */ },
                    isSending = uiState.isSending,
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
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
                    modifier = Modifier
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
                modifier = Modifier
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
                    modifier = Modifier.padding(
                        horizontal = SanchrTheme.spacing.default,
                        vertical = 2.dp,
                    ),
                )
            }

            // --- Messages list ---
            LazyColumn(
                modifier = Modifier
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
                    when (message.contentType) {
                        "image" -> ImageMessageBubble(
                            message = message,
                        )

                        else -> MessageBubble(
                            message = message,
                        )
                    }
                }
            }
        }
    }
}

// --- Top app bar ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDetailTopBar(
    title: String,
    statusText: String?,
    isOnline: Boolean,
    onNavigateBack: () -> Unit,
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
                    modifier = Modifier
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
                            color = if (isOnline) SanchrSuccess else SanchrGray400,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onVideoCall) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Video call",
                )
            }
            IconButton(onClick = onVoiceCall) {
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// --- Message bubble ---
@Composable
private fun MessageBubble(
    message: MessageUiModel,
    modifier: Modifier = Modifier,
) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = if (message.isFromMe) {
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
            Surface(
                shape = bubbleShape,
                color = Color.Transparent,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Box(
                    modifier = if (message.isFromMe) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(SanchrIndigo500, SanchrIndigo900),
                            ),
                        )
                    } else {
                        Modifier.background(SanchrGray100)
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = SanchrTheme.spacing.md,
                            vertical = SanchrTheme.spacing.sm,
                        ),
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isFromMe) SanchrWhite else SanchrGray900,
                        )
                    }
                }
            }

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
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = when (message.status) {
                            MessageStatus.READ -> "Read"
                            MessageStatus.DELIVERED -> "Delivered"
                            else -> "Sent"
                        },
                        modifier = Modifier.size(14.dp),
                        tint = when (message.status) {
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
    }
}

// --- Image message bubble ---
@Composable
private fun ImageMessageBubble(
    message: MessageUiModel,
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
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isFromMe) SanchrIndigo500 else SanchrGray100,
                ),
            ) {
                // TODO: Load image with AsyncImage/Coil
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(SanchrGray100),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "[Image]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SanchrGray400,
                    )
                }
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
    onAttach: () -> Unit,
    isSending: Boolean,
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
            verticalAlignment = Alignment.Bottom,
        ) {
            // "+" attach button
            IconButton(
                onClick = onAttach,
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
                    IconButton(onClick = { /* TODO: emoji picker */ }) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEmotions,
                            contentDescription = "Emoji",
                            tint = SanchrGray400,
                        )
                    }
                },
                maxLines = 4,
                shape = SanchrShapeTokens.CornerFull,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SanchrIndigo500,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))

            // Paperclip / attach file
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isSending,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
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
