package com.sanchr.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Privacy",
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
            Text(
                text = "Messaging Privacy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Read Receipts",
                subtitle = "Let others see when you've read their messages",
                checked = uiState.readReceiptsEnabled,
                onCheckedChange = viewModel::setReadReceiptsEnabled,
            )

            SettingsToggleRow(
                title = "Online Status",
                subtitle = "Show when you are currently online",
                checked = uiState.onlineStatusVisible,
                onCheckedChange = viewModel::setOnlineStatusVisible,
            )

            SettingsToggleRow(
                title = "Typing Indicators",
                subtitle = "Show when you are typing a message",
                checked = uiState.typingIndicatorsEnabled,
                onCheckedChange = viewModel::setTypingIndicatorsEnabled,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Profile Photo Visibility dropdown
            Text(
                text = "Profile Photo Visibility",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            var expanded by remember { mutableStateOf(false) }
            val options = listOf("Everyone", "Contacts Only", "Nobody")
            val currentLabel = if (uiState.profilePhotoVisible) "Everyone" else "Nobody"

            Column {
                Text(
                    text = "Who can see your profile photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                        .padding(vertical = SanchrTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                expanded = false
                                viewModel.setProfilePhotoVisible(option != "Nobody")
                            },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Disappearing Messages default duration
            Text(
                text = "Disappearing Messages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Set a default timer for new conversations. Messages will be automatically deleted after the timer expires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            val durations = listOf(
                "off" to "Off",
                "30s" to "30 seconds",
                "5m" to "5 minutes",
                "1h" to "1 hour",
                "24h" to "24 hours",
                "7d" to "7 days",
            )

            durations.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDisappearingMessagesDefault(value) }
                        .padding(vertical = SanchrTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = uiState.disappearingMessagesDefault == value,
                        onClick = { viewModel.setDisappearingMessagesDefault(value) },
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = SanchrTheme.spacing.sm),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}
