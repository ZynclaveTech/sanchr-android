package com.sanchr.feature.chats.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.ChatWallpapers
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Picks the wallpaper for one conversation.
 *
 * Default means "follow the account-wide choice" rather than a colour of its
 * own, so changing the global one still reaches every chat that never picked
 * its own.
 */
@Composable
fun WallpaperScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val selected = uiState.conversation?.wallpaper ?: ChatWallpapers.DEFAULT

    Scaffold(
        topBar = { SanchrTopBar(title = "Wallpaper", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SanchrTheme.spacing.default),
        ) {
            Text(
                text = "Applies to this chat only. Default follows your Appearance setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SanchrTheme.spacing.default),
            )
            SanchrCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
                    horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
                ) {
                    ChatWallpapers.NAMES.forEach { name ->
                        WallpaperSwatch(
                            name = name,
                            isSelected = name == selected,
                            darkTheme = dark,
                            onClick = { viewModel.setWallpaper(name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperSwatch(
    name: String,
    isSelected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val color = ChatWallpapers.colorOf(name, darkTheme) ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(color)
                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
