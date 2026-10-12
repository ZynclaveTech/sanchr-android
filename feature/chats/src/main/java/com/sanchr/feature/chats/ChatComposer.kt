package com.sanchr.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.IosSpacing
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo950
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWhite
import com.sanchr.feature.chats.voice.VoiceClip
import com.sanchr.feature.chats.voice.VoiceRecordButton

/**
 * The chat footer, laid out as iOS's `ChatInputBarView`: a tinted plus, the
 * message field, and one trailing control that is the mic until there is
 * something to send and the send arrow after.
 *
 * It was a Material outlined field between four controls — a bare plus that
 * opened the file picker, an emoji face, a paperclip that opened the real
 * attachment menu, and an indigo disc for the mic — on a raised surface. Two
 * of the four were both called "Attach" and did different things, and nothing
 * about the row matched the same screen on an iPhone.
 */
@Composable
internal fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enterSendsMessage: Boolean,
    onSend: () -> Unit,
    onEmojiClick: () -> Unit,
    onAttachSticker: () -> Unit,
    onAttachFile: () -> Unit,
    onAttachPhotos: () -> Unit,
    onCapturePhoto: () -> Unit,
    onAttachLocation: () -> Unit,
    onAttachViewOnce: () -> Unit,
    onAttachContact: () -> Unit,
    onVoiceClip: (VoiceClip) -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasInput = value.isNotBlank()
    // Set while a voice message is being recorded or reviewed, when the
    // recorder takes the whole row.
    var capturingVoice by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // The screen's own ground rather than a raised surface: on iOS
                // the footer is part of the page, not a bar laid over it.
                .background(MaterialTheme.colorScheme.background)
                // The keyboard's height less the navigation bar's, which the
                // app's own scaffold has already cleared. The whole of it left
                // the footer floating a bar's height above the keys.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .padding(start = BAR_HORIZONTAL, end = BAR_HORIZONTAL, top = BAR_TOP, bottom = BAR_BOTTOM),
        // Bottom-aligned, so the controls stay with the last line as the
        // field grows.
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
    ) {
        if (!capturingVoice) {
            AttachmentsButton(
                entries =
                    listOf(
                        AttachmentEntry("Camera", Icons.Filled.PhotoCamera, onCapturePhoto),
                        AttachmentEntry("Photos", Icons.Filled.PhotoLibrary, onAttachPhotos),
                        AttachmentEntry("File", Icons.Filled.AttachFile, onAttachFile),
                        AttachmentEntry("View-once photo", Icons.Filled.LocalFireDepartment, onAttachViewOnce),
                        AttachmentEntry("Location", Icons.Filled.Place, onAttachLocation),
                        AttachmentEntry("Contact", Icons.Filled.Person, onAttachContact),
                        AttachmentEntry("Sticker", Icons.Filled.EmojiEmotions, onAttachSticker),
                    ),
                modifier = Modifier.padding(bottom = SIDE_CONTROL_LIFT),
            )
            ComposerField(
                value = value,
                onValueChange = onValueChange,
                enterSendsMessage = enterSendsMessage,
                onSend = { if (hasInput && !isSending) onSend() },
                onEmojiClick = onEmojiClick,
                showEmoji = !hasInput,
                modifier = Modifier.weight(1f),
            )
        }
        if (hasInput) {
            SendButton(
                onClick = onSend,
                enabled = !isSending,
                modifier = Modifier.padding(bottom = SIDE_CONTROL_LIFT),
            )
        } else {
            // Nothing typed: the trailing action is a voice note.
            VoiceRecordButton(
                enabled = !isSending,
                onClip = onVoiceClip,
                modifier =
                    Modifier
                        .padding(bottom = SIDE_CONTROL_LIFT)
                        .then(if (capturingVoice) Modifier.weight(1f) else Modifier),
                onCapturingChange = { capturingVoice = it },
            )
        }
    }
}

private data class AttachmentEntry(
    val label: String,
    val icon: ImageVector,
    val onPick: () -> Unit,
)

/** The plus: a 36dp disc tinted with the primary colour, which turns into a close mark while its menu is open. */
@Composable
private fun AttachmentsButton(
    entries: List<AttachmentEntry>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(IosSpacing.composerControl)
                    .clip(CircleShape)
                    .background(SanchrIndigo500.copy(alpha = PLUS_TINT_ALPHA))
                    .clickable(
                        onClickLabel = if (open) "Close" else "Send a photo, video, document, location or contact",
                        role = Role.Button,
                    ) { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (open) "Close" else "Attachments",
                tint = SanchrIndigo500,
                modifier = Modifier.size(GLYPH_SIZE),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        entry.onPick()
                    },
                )
            }
        }
    }
}

/**
 * The message field: a rounded surface with a hairline that takes the primary
 * colour while focused, and the emoji face inside it until something is typed.
 */
@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    enterSendsMessage: Boolean,
    onSend: () -> Unit,
    onEmojiClick: () -> Unit,
    showEmoji: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(IosSpacing.composerFieldRadius)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = QUIET_ALPHA)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = IosType.messageBubbleText.copy(color = MaterialTheme.colorScheme.onSurface),
        // The Enter-sends setting: iOS switches the return key between Send
        // and Return on the same preference.
        keyboardOptions =
            KeyboardOptions(
                imeAction = if (enterSendsMessage) ImeAction.Send else ImeAction.Default,
                capitalization = KeyboardCapitalization.Sentences,
            ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        maxLines = FIELD_MAX_LINES,
        interactionSource = interaction,
        cursorBrush = SolidColor(SanchrIndigo500),
        decorationBox = { field ->
            Row(
                modifier =
                    Modifier
                        .clip(shape)
                        .background(SanchrTheme.surfaces.surface)
                        .border(
                            width = 1.dp,
                            color =
                                if (focused) {
                                    SanchrIndigo500.copy(alpha = FOCUSED_STROKE_ALPHA)
                                } else {
                                    SanchrTheme.surfaces.line.copy(alpha = RESTING_STROKE_ALPHA)
                                },
                            shape = shape,
                        ).padding(horizontal = FIELD_HORIZONTAL, vertical = FIELD_VERTICAL)
                        // Held at the height the emoji face gives the row, so
                        // the field does not shrink the moment typing starts
                        // and the face leaves.
                        .heightIn(min = FIELD_ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FIELD_GAP),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(text = "Message...", style = IosType.messageBubbleText, color = quiet)
                    }
                    field()
                }
                if (showEmoji) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfied,
                        contentDescription = "Emoji",
                        tint = quiet,
                        modifier =
                            Modifier
                                .size(FIELD_ROW_HEIGHT)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = onEmojiClick),
                    )
                }
            }
        },
    )
}

/** Send: a 36dp disc in the primary gradient with an upward arrow. */
@Composable
private fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(IosSpacing.composerControl)
                // Dimmed between tapping send and the composer clearing, the
                // only moment a second tap could land.
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(SanchrIndigo500, SanchrIndigo950)))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = "Send",
            tint = SanchrWhite,
            modifier = Modifier.size(GLYPH_SIZE),
        )
    }
}

// iOS: .padding(.horizontal, 12), .padding(.top, 10), .padding(.bottom, 6).
private val BAR_HORIZONTAL = 12.dp
private val BAR_TOP = 10.dp
private val BAR_BOTTOM = 6.dp
private val CONTROL_GAP = 10.dp

// iOS: .padding(.horizontal, 14), .padding(.vertical, 10), HStack(spacing: 6).
private val FIELD_HORIZONTAL = 14.dp
private val FIELD_VERTICAL = 10.dp
private val FIELD_GAP = 6.dp

/** One line of the field: with the vertical padding, 42dp against iOS's 41.5pt. */
private val FIELD_ROW_HEIGHT = 22.dp

/**
 * Lifts the 36dp side controls onto the centre of a one-line field, which is
 * 42dp tall; with more lines they still sit by the last one. iOS's
 * `sideControlBottomInset`.
 */
private val SIDE_CONTROL_LIFT = 3.dp
private val GLYPH_SIZE = 18.dp
private const val FIELD_MAX_LINES = 5
private const val PLUS_TINT_ALPHA = 0.1f
private const val FOCUSED_STROKE_ALPHA = 0.4f
private const val RESTING_STROKE_ALPHA = 0.6f
private const val QUIET_ALPHA = 0.7f
private const val DISABLED_ALPHA = 0.5f
