package com.sanchr.feature.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    onStartCall: (peerId: String, peerName: String, isVideo: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pickAvatar = rememberAvatarPicker(onPicked = viewModel::uploadAvatar)
    ProfileEvents(viewModel = viewModel, onOpenConversation = onOpenConversation)

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = if (uiState.isOwnProfile) "My Profile" else "Profile",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (uiState.isOwnProfile) {
                        if (uiState.isEditing) {
                            IconButton(onClick = { viewModel.toggleEditMode() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                )
                            }
                            IconButton(
                                onClick = { viewModel.saveProfile() },
                                enabled = !uiState.isSaving,
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Save",
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleEditMode() }) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit profile",
                                )
                            }
                        }
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // Large avatar (120dp) with edit overlay
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = "${uiState.displayName} avatar",
                        modifier =
                            Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(120.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text =
                                    uiState.displayName
                                        .take(1)
                                        .uppercase()
                                        .ifEmpty { "?" },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                // Camera overlay for own profile
                if (uiState.isOwnProfile) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .align(Alignment.BottomEnd)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ).clickable(enabled = !uiState.isUploadingAvatar) {
                                    pickAvatar.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Change avatar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))

            if (uiState.isEditing) {
                // Edit mode
                SanchrTextField(
                    value = uiState.editDisplayName,
                    onValueChange = viewModel::onEditDisplayNameChanged,
                    label = "Display Name",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SanchrTheme.spacing.xxl),
                )

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                SanchrTextField(
                    value = uiState.editBio,
                    onValueChange = viewModel::onEditBioChanged,
                    label = "Status / Bio",
                    singleLine = false,
                    maxLines = 3,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SanchrTheme.spacing.xxl),
                )
            } else {
                // Display mode
                Text(
                    text = uiState.displayName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (uiState.phoneNumber.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxs))
                    Text(
                        text = uiState.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (uiState.bio.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                    Text(
                        text = uiState.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = SanchrTheme.spacing.xxl),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // QR Share button (own profile)
            if (uiState.isOwnProfile && !uiState.isEditing) {
                SanchrButton(
                    onClick = { /* show QR code */ },
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
                    Text("Share QR Code")
                }
            }

            // Action buttons (other users only)
            if (!uiState.isOwnProfile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ProfileActionButton(
                        icon = Icons.Filled.Chat,
                        label = "Message",
                        onClick = viewModel::openConversation,
                    )

                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.xxl))

                    ProfileActionButton(
                        icon = Icons.Filled.Call,
                        label = "Voice",
                        onClick = { onStartCall(userId, uiState.displayName, false) },
                    )

                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.xxl))

                    ProfileActionButton(
                        icon = Icons.Filled.Videocam,
                        label = "Video",
                        onClick = { onStartCall(userId, uiState.displayName, true) },
                    )
                }

                Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
                BlockControl(
                    displayName = uiState.displayName,
                    isBlocked = uiState.isBlocked,
                    onSetBlocked = viewModel::setBlocked,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // Info card with phone and status
            if (!uiState.isEditing) {
                SanchrCard(
                    modifier = Modifier.padding(horizontal = SanchrTheme.spacing.default),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(SanchrTheme.spacing.default),
                    ) {
                        if (uiState.phoneNumber.isNotEmpty()) {
                            ProfileInfoRow(label = "Phone", value = uiState.phoneNumber)
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm),
                            )
                        }
                        ProfileInfoRow(
                            label = "Status",
                            value = uiState.bio.ifEmpty { "No status set" },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}

@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        OutlinedIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The system photo picker: needs no storage permission, and the app never
 * sees anything but the one image the user chose. Reads the bytes off the
 * main thread and hands them to [onPicked] with their MIME type.
 */
@Composable
private fun rememberAvatarPicker(
    onPicked: (bytes: ByteArray, contentType: String) -> Unit,
): ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked =
                withContext(Dispatchers.IO) {
                    val type = context.contentResolver.getType(uri) ?: "image/jpeg"
                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?.let { it to type }
                }
            if (picked != null) onPicked(picked.first, picked.second)
        }
    }
}

/** Block / Unblock for someone else's profile; blocking asks first, unblocking does not. */
@Composable
private fun BlockControl(
    displayName: String,
    isBlocked: Boolean,
    onSetBlocked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmBlock by remember { mutableStateOf(false) }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block ${displayName.ifBlank { "this contact" }}?") },
            text = { Text("They won't be able to message or call you. They won't be told.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBlock = false
                        onSetBlocked(true)
                    },
                ) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
        )
    }
    TextButton(
        onClick = { if (isBlocked) onSetBlocked(false) else confirmBlock = true },
        modifier = modifier,
    ) {
        Icon(imageVector = Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))
        Text(text = if (isBlocked) "Unblock" else "Block", color = MaterialTheme.colorScheme.error)
    }
}

/** Routes the view model's one-shot events: open the resolved chat, or show why it could not. */
@Composable
private fun ProfileEvents(
    viewModel: ProfileViewModel,
    onOpenConversation: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.OpenConversation -> onOpenConversation(event.conversationId)
                is ProfileEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                ProfileEvent.ProfileSaved -> Unit
            }
        }
    }
}
