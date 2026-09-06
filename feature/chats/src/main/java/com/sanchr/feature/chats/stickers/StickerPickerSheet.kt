package com.sanchr.feature.chats.stickers

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * The composer's sticker picker (iOS `StickerPickerSheet`): pack tabs over a
 * grid.
 *
 * Unlike the emoji picker, tapping sends immediately and closes the sheet —
 * a sticker is the whole message, not something added to one, and iOS
 * behaves the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var pack by remember { mutableStateOf(StickerPack.FACES) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SanchrTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickerPack.entries.forEach { entry ->
                    Text(
                        text = entry.icon,
                        style = MaterialTheme.typography.titleLarge,
                        color =
                            if (entry == pack) {
                                SanchrIndigo500
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier =
                            Modifier
                                .clickable { pack = entry }
                                .padding(SanchrTheme.spacing.xs)
                                .semanticsLabel(entry.label),
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(SanchrTheme.spacing.xs),
            ) {
                items(pack.stickers) { sticker ->
                    Text(
                        text = sticker,
                        style = MaterialTheme.typography.displaySmall,
                        modifier =
                            Modifier
                                .clickable {
                                    onSelect(sticker)
                                    onDismiss()
                                }.padding(SanchrTheme.spacing.xs),
                    )
                }
            }
        }
    }
}

/** The pack name for screen readers, since the tab itself is only an emoji. */
private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(
        Modifier.semantics { contentDescription = label },
    )
