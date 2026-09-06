package com.sanchr.feature.chats.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * How long messages sent in this one conversation survive.
 *
 * Settings already offered an account-wide default and the send path already
 * preferred a per-conversation value, but nothing could set one, so the
 * per-chat column was dead and every chat used the global timer.
 */
@Composable
fun DisappearingMessagesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SanchrTopBar(title = "Disappearing messages", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SanchrTheme.spacing.default),
        ) {
            Text(
                text =
                    "New messages you send in this chat disappear after the chosen time. " +
                        "Off uses your account-wide default instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SanchrTheme.spacing.default),
            )
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DisappearingDurations.LABELS.forEach { label ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = uiState.disappearingLabel == label,
                                        onClick = { viewModel.setDisappearing(label) },
                                    ).padding(SanchrTheme.spacing.default),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = uiState.disappearingLabel == label,
                                onClick = { viewModel.setDisappearing(label) },
                            )
                            Text(
                                text = DisappearingDurations.displayName(label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = SanchrTheme.spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}
