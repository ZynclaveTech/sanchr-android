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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Settings › Terms & Privacy (iOS `LegalView`). Both stores require the
 * policy to be reachable from inside the app, and the Privacy row used to
 * lead to the privacy *toggles*, where a reviewer would find no policy.
 */
@Composable
fun LegalScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = { SanchrTopBar(title = "Terms & Privacy", onNavigateBack = onNavigateBack) },
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
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default)) {
                    Text(
                        text = "Built so we can't read your messages",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "These documents say exactly what Sanchr keeps, what it never sees, and the rules for using it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))

            SettingsItem(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "What we can and cannot see, and why",
                onClick = { SupportLinks.openUrl(context, SupportLinks.PRIVACY_POLICY) },
            )

            SettingsItem(
                icon = Icons.Filled.Description,
                title = "Terms of Service",
                subtitle = "The agreement for using Sanchr",
                onClick = { SupportLinks.openUrl(context, SupportLinks.TERMS_OF_SERVICE) },
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = SanchrGray400,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                Text(
                    text = "Sanchr ${SupportLinks.appVersion(context)} · Zynclave Tech Private Limited",
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
