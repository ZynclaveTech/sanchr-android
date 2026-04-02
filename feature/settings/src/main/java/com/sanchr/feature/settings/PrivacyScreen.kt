package com.sanchr.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                title = "Typing Indicators",
                subtitle = "Show when you're typing a message",
                checked = uiState.typingIndicatorsEnabled,
                onCheckedChange = viewModel::setTypingIndicatorsEnabled,
            )

            SettingsToggleRow(
                title = "Last Active",
                subtitle = "Show when you were last online",
                checked = uiState.lastActiveVisible,
                onCheckedChange = viewModel::setLastActiveVisible,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            Text(
                text = "Disappearing Messages",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Set a default timer for new conversations. Messages will be automatically deleted after the timer expires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // TODO: Disappearing message duration picker (Off, 30s, 5m, 1h, 24h, 7d)

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            Text(
                text = "Blocked Contacts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // TODO: List of blocked contacts with unblock option
        }
    }
}
