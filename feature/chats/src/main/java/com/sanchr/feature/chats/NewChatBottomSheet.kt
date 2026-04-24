package com.sanchr.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Bottom sheet for starting a new direct conversation by E.164 phone number.
 * Purely a dumb renderer of [NewChatState]; all business logic lives on
 * [ChatsListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatBottomSheet(
    state: NewChatState,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SanchrTheme.spacing.default,
                        vertical = SanchrTheme.spacing.md,
                    ),
        ) {
            Text(
                text = "New conversation",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))

            SanchrTextField(
                value = state.phone,
                onValueChange = onPhoneChange,
                placeholder = "+1 555 123 4567",
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Surface(
                    shape = SanchrShapeTokens.CornerMedium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.error.toUserMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier =
                            Modifier.padding(
                                horizontal = SanchrTheme.spacing.md,
                                vertical = SanchrTheme.spacing.sm,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
            SanchrButton(
                text = "Start chat",
                onClick = onSubmit,
                isLoading = state.isSubmitting,
                enabled = state.phone.isNotBlank() && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))
        }
    }
}

private fun NewChatError.toUserMessage(): String =
    when (this) {
        NewChatError.INVALID_PHONE -> "Enter a valid phone number (e.g. +1 555 123 4567)"
        NewChatError.NOT_FOUND -> "That number isn't on Sanchr"
        NewChatError.SERVER_ERROR -> "Couldn't reach server, try again"
    }
