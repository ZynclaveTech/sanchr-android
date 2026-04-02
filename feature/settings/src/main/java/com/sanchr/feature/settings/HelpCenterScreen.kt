package com.sanchr.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun HelpCenterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Help Center",
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
            SettingsItem(
                icon = Icons.Filled.QuestionAnswer,
                title = "FAQ",
                subtitle = "Frequently asked questions",
                onClick = {
                    // TODO: Open FAQ in WebView or external browser
                },
            )

            SettingsItem(
                icon = Icons.Filled.Email,
                title = "Contact Support",
                subtitle = "Send us an email",
                onClick = {
                    // TODO: Launch email intent to support@sanchr.com
                },
            )

            SettingsItem(
                icon = Icons.Filled.BugReport,
                title = "Report a Problem",
                subtitle = "Help us improve Sanchr",
                onClick = {
                    // TODO: Open bug report form
                },
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            Text(
                text = "Legal",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            // TODO: Terms of Service link
            // TODO: Privacy Policy link
            // TODO: Open Source Licenses link

            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm),
            )

            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm),
            )

            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = SanchrTheme.spacing.sm),
            )
        }
    }
}
