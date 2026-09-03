package com.sanchr.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun SecurityScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Security",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            // Screen Lock section
            Text(
                text = "Screen Lock",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Screen Lock",
                subtitle = "Require authentication to open Sanchr",
                checked = uiState.screenLockEnabled,
                onCheckedChange = viewModel::setScreenLockEnabled,
            )

            // Lock timeout picker
            if (uiState.screenLockEnabled) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Text(
                    text = "Lock Timeout",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))

                val timeouts =
                    listOf(
                        "immediately" to "Immediately",
                        "1m" to "After 1 minute",
                        "5m" to "After 5 minutes",
                        "30m" to "After 30 minutes",
                    )

                timeouts.forEach { (value, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setScreenLockTimeout(value) }
                                .padding(vertical = SanchrTheme.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.screenLockTimeout == value,
                            onClick = { viewModel.setScreenLockTimeout(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = SanchrTheme.spacing.sm),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Biometric Authentication
            Text(
                text = "Biometric Authentication",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Biometric Unlock",
                subtitle = "Use fingerprint or face recognition to unlock Sanchr",
                checked = uiState.biometricEnabled,
                onCheckedChange = viewModel::setBiometricEnabled,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Screenshot Protection
            Text(
                text = "Screen Security",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SettingsToggleRow(
                title = "Screenshot Protection",
                subtitle = "Prevent screenshots and app preview in recent apps (FLAG_SECURE)",
                checked = uiState.screenshotProtection,
                onCheckedChange = viewModel::setScreenshotProtection,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Sanchr Mode
            Text(
                text = "Sanchr Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            SanchrCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                ) {
                    Text(
                        text = "Sanchr Mode hides sensitive conversations and vault items when enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
                    SettingsToggleRow(
                        title = "Enable Sanchr Mode",
                        subtitle = null,
                        checked = uiState.sanchrModeEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleSanchrMode(enabled)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}
