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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.CountryCodePicker
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.component.findCountryByDialCode
import com.sanchr.core.designsystem.component.resolveDefaultCountry
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
 * Returning-user phone entry. Android counterpart of iOS `LoginView` phone
 * section (LoginView.swift:97-179). Observes [AuthViewModel.state] and renders
 * keyed off [AuthState.LoginPhone]; any other state means the NavHost observer
 * has already moved the flow forward, so this composable renders an empty
 * [Box] to avoid a last-frame flicker during route transitions.
 *
 * This screen is the canonical landing after the splash, matching iOS
 * `SanchrApp.swift:350-396` (`Splash -> LoginView`). New users reach the
 * register path via the "New to Sanchr? Sign up" footer affordance which
 * invokes [AuthViewModel.switchToRegister] — iOS has no equivalent because
 * its backend exposes a single Register-for-everyone call.
 *
 * iOS-parity copy synced 2026-04-25 against `LoginView.swift`. Hero title
 * ("Welcome to Sanchr"), tagline ("Encrypted. Synced. Secure."), phone
 * label ("Phone Number"), placeholder ("(555) 123-4567"), helper text
 * ("We'll send you a verification code"), security card title
 * ("End-to-End Encrypted") and body ("Your messages are secured with
 * military-grade encryption. Only you and your contacts can read them.")
 * are taken character-for-character from `LoginView.swift:81-199`.
 *
 * iOS-deviation: the OutlinedTextField label uses Title Case "Phone Number"
 * mirroring iOS line 99; this also doubles as the Material floating-label,
 * which Android requires (iOS draws a separate label above the field).
 *
 * TODO(copy): the Privacy Policy / Terms URLs below point at the placeholder
 *   `sanchr.app/privacy` and `sanchr.app/terms`. Replace with the final
 *   marketing URLs once legal sign-off lands.
 */
@Composable
fun LoginPhoneScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val deviceDefault = remember(context) { resolveDefaultCountry(context) }

    val loginPhone: AuthState.LoginPhone =
        when (val s = state) {
            is AuthState.LoginPhone -> s
            is AuthState.Error ->
                s.previousState as? AuthState.LoginPhone ?: run {
                    Box(modifier = modifier.fillMaxSize())
                    return
                }
            else -> {
                Box(modifier = modifier.fillMaxSize())
                return
            }
        }

    LaunchedEffect(deviceDefault.dialCode) {
        if (loginPhone.phone.isEmpty() &&
            loginPhone.countryCode == "+1" &&
            deviceDefault.dialCode != "+1"
        ) {
            viewModel.onLoginPhoneChanged(deviceDefault.dialCode, "")
        }
    }

    val selectedCountry =
        findCountryByDialCode(loginPhone.countryCode)
            ?: deviceDefault.takeIf { it.dialCode == loginPhone.countryCode }
            ?: deviceDefault

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
                value = loginPhone.phone,
                onValueChange = { viewModel.onLoginPhoneChanged(loginPhone.countryCode, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone Number") },
                placeholder = { Text("(555) 123-4567") },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        CountryCodePicker(
                            selected = selectedCountry,
                            onSelected = { country ->
                                viewModel.onLoginPhoneChanged(country.dialCode, loginPhone.phone)
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
                            text = "Your messages are secured with military-grade encryption. Only you and your contacts can read them.",
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
                    viewModel.submitLoginPhone()
                },
                enabled = loginPhone.phone.length >= MIN_CONTINUE_DIGITS && !loginPhone.isSubmitting,
                isLoading = loginPhone.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // iOS-deviation: iOS LoginView shows a single combined sentence
            // ("By continuing, you agree to our Privacy Policy and Terms of
            // Service") whereas Android exposes the two documents as
            // separate tappable links — Material idiom for legal footers.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                SanchrTextButton(onClick = { uriHandler.openUri(PRIVACY_URL) }) {
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
                SanchrTextButton(onClick = { uriHandler.openUri(TERMS_URL) }) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.labelSmall,
                        color = SanchrIndigo500,
                    )
                }
            }

            // iOS-deviation: iOS surfaces no "Sign up" affordance because its
            // Register endpoint is called for every first-time verification.
            // Android's backend still exposes split Register/Login entry so we
            // offer an explicit switch to the register flow. Placement below
            // the legal footer keeps the primary CTA (Continue) visually
            // dominant while still giving new users a discoverable path.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanchrTheme.spacing.xl),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New to Sanchr?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SanchrGray500,
                )
                SanchrTextButton(onClick = { viewModel.switchToRegister() }) {
                    Text(
                        text = "Sign up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SanchrIndigo500,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private const val MIN_CONTINUE_DIGITS = 7
private const val PRIVACY_URL = "https://sanchr.app/privacy"
private const val TERMS_URL = "https://sanchr.app/terms"
