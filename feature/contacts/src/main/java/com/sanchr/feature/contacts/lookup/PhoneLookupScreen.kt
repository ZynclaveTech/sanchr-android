package com.sanchr.feature.contacts.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Finds someone by phone number without needing them in the address book,
 * and opens a chat.
 *
 * Mirrors iOS `PhoneNumberLookupView`.
 */
@Composable
fun PhoneLookupScreen(
    onNavigateBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneLookupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SanchrTopBar(title = "Find by number", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default),
        ) {
            SanchrTextField(
                value = uiState.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChanged,
                placeholder = "Phone number with country code",
                modifier = Modifier.fillMaxWidth(),
            )
            SanchrButton(
                text = "Search",
                onClick = viewModel::search,
                enabled = uiState.canSearch,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val result = uiState.result) {
                LookupResult.Idle -> Unit
                LookupResult.Searching -> CircularProgressIndicator()
                LookupResult.NotFound ->
                    Notice("Nobody on Sanchr is using that number.")
                // Kept distinct from "not found": a failed search must not be
                // read as proof that the contact is not on Sanchr.
                is LookupResult.Failed -> Notice(result.message)
                is LookupResult.Found ->
                    SanchrCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.user.displayName.ifBlank { result.user.phoneNumber },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = result.user.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SanchrButton(
                                text = "Message",
                                onClick = { viewModel.startChat(onOpenConversation) },
                                enabled = !uiState.isStartingChat,
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun Notice(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
