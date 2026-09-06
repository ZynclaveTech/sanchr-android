package com.sanchr.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.Conversation
import com.sanchr.core.model.VaultItem

/**
 * Where a vault item is going.
 *
 * Two destinations, mirroring iOS `VaultShareDestinationSheet`, and the
 * difference between them matters: sending into a chat keeps the item
 * encrypted end to end and it never touches the filesystem, while sharing
 * outside decrypts it and hands the bytes to another app. The second option
 * says so before it is chosen, not after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultShareSheet(
    item: VaultItem,
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onShareInChat: (String) -> Unit,
    onShareOutside: () -> Unit,
) {
    var pickingConversation by remember(item.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = SanchrTheme.spacing.default)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = SanchrTheme.spacing.default),
            )

            if (pickingConversation) {
                if (conversations.isEmpty()) {
                    Text(
                        text = "No conversations yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(SanchrTheme.spacing.default),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(conversations) { conversation ->
                            ListItem(
                                headlineContent = { Text(conversation.title?.takeIf { it.isNotBlank() } ?: "Conversation") },
                                modifier = Modifier.clickable { onShareInChat(conversation.id) },
                            )
                        }
                    }
                }
            } else {
                ListItem(
                    headlineContent = { Text("Share in chat") },
                    supportingContent = { Text("Stays end-to-end encrypted") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    modifier = Modifier.clickable { pickingConversation = true },
                )
                ListItem(
                    headlineContent = { Text("Share outside Sanchr") },
                    supportingContent = {
                        Text("The app you choose receives the decrypted file.")
                    },
                    leadingContent = { Icon(Icons.Filled.IosShare, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onShareOutside),
                )
            }
        }
    }
}
