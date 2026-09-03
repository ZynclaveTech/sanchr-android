package com.sanchr.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.User

/**
 * Bottom sheet that lets the user pick an already-synced contact to start a
 * new direct conversation.
 *
 * iOS parity: mirrors NewChatContactPickerSheet at
 * ios/Sanchr-iOS/Features/Chats/Presentation/NewChatContactPicker.swift.
 *
 * Phone-number lookup belongs to a separate (out-of-scope here) "Add
 * contact" entry from the Contacts tab — not the new-chat FAB.
 *
 * Pure renderer of [NewChatPickerState]; all logic on [ChatsListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatContactPickerSheet(
    state: NewChatPickerState,
    onSearchQueryChanged: (String) -> Unit,
    onContactSelected: (User) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 640.dp)
                    .padding(bottom = SanchrTheme.spacing.default),
        ) {
            Text(
                text = "New Chat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.sm,
                        ),
            )

            SanchrTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = "Search contacts...",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = SanchrGray400,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.default,
                            vertical = SanchrTheme.spacing.xs,
                        ),
            )

            if (state.error != null) {
                ErrorBanner(
                    message = state.error,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = SanchrTheme.spacing.default,
                                vertical = SanchrTheme.spacing.xs,
                            ),
                )
            }

            PickerBody(
                state = state,
                onContactSelected = onContactSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        }
    }
}

@Composable
private fun PickerBody(
    state: NewChatPickerState,
    onContactSelected: (User) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.contacts.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SanchrIndigo500)
            }
        }

        state.filteredContacts.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(SanchrTheme.spacing.xxl),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = SanchrGray400,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                    Text(
                        text =
                            if (state.contacts.isEmpty()) {
                                "No contacts yet"
                            } else {
                                "No matching contacts"
                            },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "Synced Sanchr contacts will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SanchrGray500,
                    )
                }
            }
        }

        else -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(
                    items = state.filteredContacts,
                    key = { it.id },
                ) { contact ->
                    ContactPickerRow(
                        contact = contact,
                        onClick = { onContactSelected(contact) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SanchrShapeTokens.CornerMedium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.padding(
                    horizontal = SanchrTheme.spacing.md,
                    vertical = SanchrTheme.spacing.sm,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ContactPickerRow(
    contact: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = SanchrTheme.spacing.default,
                    vertical = SanchrTheme.spacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — 44dp circle, AsyncImage with initials fallback (matches
        // the conversation row's avatar treatment but slightly tighter for
        // list density inside a sheet).
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            if (contact.avatarUrl != null) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = "${contact.displayName} avatar",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            contact.displayName
                                .take(1)
                                .uppercase()
                                .ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = contact.displayName.ifBlank { contact.phoneNumber },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = contact.bio?.takeIf { it.isNotBlank() } ?: contact.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
