package com.sanchr.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo400
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo900
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWhite

/**
 * Phone-entry step of the onboarding flow. Observes [AuthViewModel.state] and
 * renders UI keyed off [AuthState.PhoneEntry]; any other state means the host
 * navigation observer has already moved the flow forward, so the screen shows
 * nothing (the NavHost will swap it out immediately).
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Resolve the PhoneEntry snapshot we need to render. If the current state is an
    // Error whose previousState is PhoneEntry, we render that PhoneEntry + inline
    // error so the user can fix and retry.
    val phoneEntry: AuthState.PhoneEntry =
        when (val s = state) {
            is AuthState.PhoneEntry -> s
            is AuthState.Error -> s.previousState as? AuthState.PhoneEntry ?: return
            else -> return // NavHost will navigate away; render nothing to avoid flicker.
        }

    val errorMessage = (state as? AuthState.Error)?.message

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SanchrTheme.spacing.xl)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.massive))

            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(SanchrShapeTokens.CornerExtraLarge)
                        .background(
                            Brush.linearGradient(colors = listOf(SanchrIndigo500, SanchrIndigo400)),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = SanchrWhite,
                    modifier = Modifier.size(36.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Sanchr logo",
                    tint = SanchrWhite.copy(alpha = 0.6f),
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 4.dp, end = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            Text(
                text = "Welcome to Sanchr",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Encrypted. Synced. Secure.",
                style = MaterialTheme.typography.bodyLarge,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            OutlinedTextField(
                value = phoneEntry.phone,
                onValueChange = { viewModel.onPhoneChanged(phoneEntry.countryCode, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("Enter your phone number") },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = "Country",
                            tint = SanchrGray500,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = phoneEntry.countryCode,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                isError = errorMessage != null,
                supportingText =
                    if (errorMessage != null) {
                        {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        {
                            Text(
                                text = "We'll send you a verification code",
                                color = SanchrGray400,
                            )
                        }
                    },
                shape = SanchrShapeTokens.CornerMedium,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SanchrIndigo500,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SanchrShapeTokens.CornerMedium,
                colors = CardDefaults.cardColors(containerColor = SanchrIndigo100),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.padding(SanchrTheme.spacing.default),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = SanchrIndigo500,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(SanchrTheme.spacing.md))
                    Column {
                        Text(
                            text = "End-to-End Encrypted",
                            style = MaterialTheme.typography.labelLarge,
                            color = SanchrIndigo900,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your messages and calls are secured with Signal Protocol encryption. Not even Sanchr can read them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SanchrIndigo500,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            SanchrButton(
                text = "Continue",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitPhone()
                },
                enabled = phoneEntry.phone.length >= 7,
                isLoading = false,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "  Or connect with  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = SanchrGray400,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.md),
            ) {
                OutlinedButton(
                    onClick = { /* TODO: Google sign-in */ },
                    modifier = Modifier.weight(1f),
                    shape = SanchrShapeTokens.CornerFull,
                ) {
                    Text(text = "Google", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { /* TODO: Apple sign-in */ },
                    modifier = Modifier.weight(1f),
                    shape = SanchrShapeTokens.CornerFull,
                ) {
                    Text(text = "Apple", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanchrTheme.spacing.xl),
                horizontalArrangement = Arrangement.Center,
            ) {
                SanchrTextButton(onClick = { /* TODO: Open Privacy Policy */ }) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = SanchrIndigo500,
                    )
                }
                Text(
                    text = "  |  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = SanchrGray400,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                SanchrTextButton(onClick = { /* TODO: Open Terms */ }) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.labelSmall,
                        color = SanchrIndigo500,
                    )
                }
            }
        }
    }
}
