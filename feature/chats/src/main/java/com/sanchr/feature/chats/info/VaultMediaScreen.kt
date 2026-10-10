package com.sanchr.feature.chats.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.model.ChatVaultPolicy

/**
 * How this one conversation treats media, as iOS `VaultMediaView`.
 *
 * Every switch starts off and belongs to this chat alone. Each one does what
 * it says the moment it is turned on; a switch that only recorded a
 * preference would be a promise about someone's photos that nothing kept.
 */
@Composable
fun VaultMediaScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val policy = uiState.conversation?.vaultPolicy ?: ChatVaultPolicy.DEFAULT
    // Nothing to switch until the conversation has loaded: a tap before then
    // would write a policy built from defaults over the one that is stored.
    val loaded = uiState.conversation != null

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SanchrTopBar(title = "Vault Media", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
        ) {
            PolicyBanner()
            SanchrCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PolicyToggle(
                        icon = Icons.Filled.Visibility,
                        title = "View Once",
                        subtitle = "Photos and videos you send here disappear after they are viewed",
                        checked = policy.viewOnceOutgoing,
                        enabled = loaded,
                        onCheckedChange = { on -> viewModel.updateVaultPolicy { it.copy(viewOnceOutgoing = on) } },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = DIVIDER_INSET))
                    PolicyToggle(
                        icon = Icons.Filled.NoPhotography,
                        title = "Capture Protection",
                        subtitle = "Block screenshots and screen recording while media from this chat is open",
                        checked = policy.captureProtection,
                        enabled = loaded,
                        onCheckedChange = { on -> viewModel.updateVaultPolicy { it.copy(captureProtection = on) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = BANNER_TINT))
                .padding(SanchrTheme.spacing.default),
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
    ) {
        Icon(imageVector = Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = "These settings apply to this chat only, on this device. They change nothing about chats you have not set them for.",
            style = IosType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PolicyToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // The whole row is the switch, so TalkBack reads the title,
                // the explanation and the state as one control.
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(SanchrTheme.spacing.default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = IosType.bodyLarge)
            Text(text = subtitle, style = IosType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private val DIVIDER_INSET = 56.dp
private const val BANNER_TINT = 0.06f
