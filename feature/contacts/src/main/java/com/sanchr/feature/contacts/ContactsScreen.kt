package com.sanchr.feature.contacts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrError
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.proto.contacts.Contact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit,
    onSyncContacts: () -> Unit,
    /** Opens the by-number search, for reaching someone not in the address book. */
    onFindByNumber: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SanchrTopBar(
                title = "Contacts",
                actions = {
                    IconButton(onClick = onFindByNumber) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Find by number",
                        )
                    }
                    IconButton(onClick = onSyncContacts) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = "Sync contacts",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // Search bar
            SanchrTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = "Search contacts...",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SanchrTheme.spacing.lg,
                            vertical = SanchrTheme.spacing.sm,
                        ),
            )

            when (val state = uiState) {
                is ContactsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is ContactsUiState.Empty -> {
                    EmptyContactsState()
                }

                is ContactsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Something went wrong",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is ContactsUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        ContactsList(
                            groupedContacts = state.groupedContacts,
                            onlineUserIds = state.onlineUserIds,
                            onContactClick = onContactClick,
                            onBlockContact = viewModel::blockContact,
                            onUnblockContact = viewModel::unblockContact,
                            onMessageContact = { userId -> onContactClick(userId) },
                            onCallContact = { /* Navigate to call */ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContactsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
            Text(
                text = "No contacts found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Sync your contacts to find friends on Sanchr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ContactsList(
    groupedContacts: Map<Char, List<Contact>>,
    onlineUserIds: Set<String>,
    onContactClick: (String) -> Unit,
    onBlockContact: (String) -> Unit,
    onUnblockContact: (String) -> Unit,
    onMessageContact: (String) -> Unit,
    onCallContact: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        groupedContacts.forEach { (letter, contacts) ->
            stickyHeader(key = "header_$letter") {
                SectionHeader(letter = letter)
            }

            items(
                items = contacts,
                key = { it.userId },
            ) { contact ->
                val dismissState = rememberSwipeToDismissBoxState()

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val direction = dismissState.dismissDirection
                        val color by animateColorAsState(
                            targetValue =
                                when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> SanchrIndigo500
                                    SwipeToDismissBoxValue.EndToStart -> SanchrError
                                    else -> MaterialTheme.colorScheme.surface
                                },
                            label = "swipe_bg_color",
                        )
                        val icon =
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> Icons.AutoMirrored.Filled.Chat
                                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Block
                                else -> Icons.AutoMirrored.Filled.Chat
                            }
                        val label =
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> "Message"
                                SwipeToDismissBoxValue.EndToStart -> "Block"
                                else -> ""
                            }
                        val alignment =
                            when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                else -> Alignment.CenterEnd
                            }

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = SanchrTheme.spacing.xl),
                            contentAlignment = alignment,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    },
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                ) {
                    ContactRow(
                        contact = contact,
                        isOnline = onlineUserIds.contains(contact.userId),
                        onClick = { onContactClick(contact.userId) },
                        onBlock = { onBlockContact(contact.userId) },
                        onUnblock = { onUnblockContact(contact.userId) },
                        onMessage = { onMessageContact(contact.userId) },
                        onCall = { onCallContact(contact.userId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(letter: Char) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(
                    horizontal = SanchrTheme.spacing.lg,
                    vertical = SanchrTheme.spacing.xs,
                ),
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    isOnline: Boolean,
    onClick: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true },
                ).padding(
                    horizontal = SanchrTheme.spacing.lg,
                    vertical = SanchrTheme.spacing.md,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with online indicator
        Box(modifier = Modifier.size(48.dp)) {
            if (contact.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = contact.avatarUrl,
                    contentDescription = "${contact.displayName} avatar",
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = contact.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Online dot
            if (isOnline) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(SanchrSuccess, CircleShape),
                )
            }
        }

        Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isOnline) "Online" else contact.bio.ifEmpty { contact.phoneNumber },
                style = MaterialTheme.typography.bodySmall,
                color = if (isOnline) SanchrSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Context menu
        Box {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Message") },
                    onClick = {
                        showContextMenu = false
                        onMessage()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Call") },
                    onClick = {
                        showContextMenu = false
                        onCall()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Call, contentDescription = null)
                    },
                )
                if (contact.isBlocked) {
                    DropdownMenuItem(
                        text = { Text("Unblock") },
                        onClick = {
                            showContextMenu = false
                            onUnblock()
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Block, contentDescription = null)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Block",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showContextMenu = false
                            onBlock()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }
}
