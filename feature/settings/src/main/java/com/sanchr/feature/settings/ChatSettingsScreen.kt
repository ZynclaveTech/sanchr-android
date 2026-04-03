package com.sanchr.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun ChatSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Chat Settings",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SanchrTheme.spacing.default),
        ) {
            // Bubble Style selector
            Text(
                text = "Bubble Style",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
            ) {
                val styles = listOf(
                    "default" to "Default",
                    "rounded" to "Rounded",
                    "flat" to "Flat",
                )
                styles.forEach { (value, label) ->
                    val isSelected = uiState.bubbleStyle == value
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { viewModel.setBubbleStyle(value) }
                            .padding(SanchrTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.ChatBubble else Icons.Filled.ChatBubbleOutline,
                            contentDescription = label,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Enter sends message
            SettingsToggleRow(
                title = "Enter Sends Message",
                subtitle = "Press Enter to send a message instead of adding a new line",
                checked = uiState.enterSendsMessage,
                onCheckedChange = viewModel::setEnterSendsMessage,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            // Media auto-save
            SettingsToggleRow(
                title = "Media Auto-Save",
                subtitle = "Automatically save received photos and videos to your device gallery",
                checked = uiState.mediaAutoSave,
                onCheckedChange = viewModel::setMediaAutoSave,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}
