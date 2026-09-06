package com.sanchr.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanchr.core.common.support.SupportLinks
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrError
import com.sanchr.core.designsystem.theme.SanchrTheme

@Composable
fun HelpCenterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            // FAQ expandable sections
            Text(
                text = "Frequently Asked Questions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            val faqs =
                listOf(
                    "How does end-to-end encryption work?" to
                        "Sanchr uses the Signal Protocol for end-to-end encryption. Messages are encrypted on your device before sending and can only be decrypted by the intended recipient. Even Sanchr cannot read your messages.",
                    "How do I enable Sanchr Mode?" to
                        "Go to Settings > Security > Sanchr Mode and toggle it on. Hidden conversations and vault items will be hidden while it is enabled.",
                    "Can I use Sanchr on multiple devices?" to
                        "Currently Sanchr supports one device per account. Multi-device support with linked devices is planned for a future update.",
                    "How do I back up my messages?" to
                        "Message backups are encrypted end-to-end and can be stored in your Vault. Go to Settings > Storage > Back Up Messages to create an encrypted backup.",
                    "What happens when I delete a message?" to
                        "Deleted messages are removed from your device. If you delete for everyone, a deletion request is sent to all recipients. Disappearing messages are automatically deleted after the timer expires.",
                )

            faqs.forEach { (question, answer) ->
                FaqItem(question = question, answer = answer)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Contact form
            Text(
                text = "Contact Support",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            var subject by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }

            SanchrTextField(
                value = subject,
                onValueChange = { subject = it },
                label = "Subject",
                placeholder = "What do you need help with?",
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrTextField(
                value = message,
                onValueChange = { message = it },
                label = "Message",
                placeholder = "Describe your issue...",
                singleLine = false,
                maxLines = 5,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrButton(
                text = "Send Message",
                onClick = {
                    SupportLinks.sendEmail(
                        context = context,
                        to = SupportLinks.SUPPORT_EMAIL,
                        subject = "[Support] $subject",
                        // Device and app details help support reproduce; nothing about the account or its messages.
                        body = "$message\n\n${SupportLinks.deviceReport(context)}",
                    )
                    subject = ""
                    message = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = subject.isNotBlank() && message.isNotBlank(),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Documentation link
            SettingsItem(
                icon = Icons.Filled.Description,
                title = "Documentation",
                subtitle = "Read the full Sanchr documentation",
                onClick = { SupportLinks.openUrl(context, SupportLinks.DOCUMENTATION) },
            )

            SettingsItem(
                icon = Icons.Filled.BugReport,
                title = "Report a Problem",
                subtitle = "Help us improve Sanchr",
                onClick = {
                    SupportLinks.sendEmail(
                        context = context,
                        to = SupportLinks.SUPPORT_EMAIL,
                        subject = "[Bug] ",
                        body = "What happened:\n\nWhat you expected:\n\n${SupportLinks.deviceReport(context)}",
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.default))

            // Emergency support card
            SanchrCard {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = SanchrError,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency Support",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SanchrError,
                        )
                        Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxs))
                        Text(
                            text = "If you believe your account has been compromised, contact emergency support immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.clickable {
                                    SupportLinks.sendEmail(
                                        context = context,
                                        to = SupportLinks.SECURITY_EMAIL,
                                        subject = "[Security] Possible account compromise",
                                        body = "Describe what happened:\n\n${SupportLinks.deviceReport(context)}",
                                    )
                                },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                                tint = SanchrError,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(SanchrTheme.spacing.xs))
                            Text(
                                text = "security@sanchr.com",
                                style = MaterialTheme.typography.labelMedium,
                                color = SanchrError,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Legal section
            Text(
                text = "Legal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            // These three used to be styled as links with an empty click
            // handler, so they read as tappable and did nothing.
            listOf(
                "Terms of Service" to SupportLinks.TERMS_OF_SERVICE,
                "Privacy Policy" to SupportLinks.PRIVACY_POLICY,
                "Open Source Licenses" to SupportLinks.OPEN_SOURCE_LICENSES,
            ).forEach { (item, url) ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(url) }
                            .padding(vertical = SanchrTheme.spacing.sm),
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}

@Composable
private fun FaqItem(
    question: String,
    answer: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = SanchrTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SanchrTheme.spacing.md),
            )
        }

        HorizontalDivider()
    }
}
