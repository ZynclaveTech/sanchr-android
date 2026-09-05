package com.sanchr.feature.chats.emoji

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * The composer's emoji picker (iOS `EmojiPickerSheet`): category tabs over a
 * grid; tapping an emoji appends it to what is being typed and leaves the
 * sheet open, so several can be picked in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var category by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SanchrTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiCategory.entries.forEach { entry ->
                    IconButton(onClick = { category = entry }) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = if (entry == category) SanchrIndigo500 else SanchrGray400,
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(EMOJI_COLUMNS),
                modifier = Modifier.fillMaxWidth().height(320.dp),
            ) {
                items(category.emojis, key = { it }) { emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier =
                            Modifier
                                .clickable { onSelect(emoji) }
                                .padding(vertical = SanchrTheme.spacing.xs),
                    )
                }
            }
        }
    }
}

private const val EMOJI_COLUMNS = 8
