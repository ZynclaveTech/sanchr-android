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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWhite

/**
 * First-time signup path. Android counterpart of iOS `RegisterView`
 * (RegisterView.swift). Matches iOS field order: profile photo placeholder,
 * display name, phone number + country picker, primary "Continue to
 * Verification" CTA, encryption notice footer.
 *
 * iOS-parity copy synced 2026-04-25 against `RegisterView.swift`. Title
 * ("Create Account"), subtitle ("Set up your Sanchr profile before
 * verification"), field labels ("Display Name", "Phone Number"),
 * placeholders ("Enter your name", "Phone number"), CTA ("Continue to
 * Verification") and helper text ("We'll verify your number before
 * creating the account") are taken character-for-character from
 * `RegisterView.swift:25-140`.
 *
 * iOS-deviation: the supporting/helper text ("We'll verify…") sits below
 * the phone field on Android because Material's [OutlinedTextField] owns
 * its supportingText slot; iOS draws the same string as a separate
 * encryption-badge capsule beneath the CTA. Layout parity is preserved
 * (helper still appears in the form group); only the visual chrome
 * differs.
 *
 * iOS-deviation: Privacy Policy / Terms footer is Android-only — iOS
 * `RegisterView` ships no legal footer (the agreement copy lives on
 * `LoginView`). Retained for legal-team request that every entry-point
 * surface the links.
 *
 * Observes [AuthViewModel.state] and renders keyed off
 * [AuthState.RegisterPhoneAndName]. Other states render an empty [Box] while
 * the NavHost transitions.
 *
 * TODO(copy): Privacy Policy / Terms URLs use the placeholder
 *   `sanchr.app/privacy` and `sanchr.app/terms`. Replace with the final
 *   marketing URLs once legal sign-off lands.
 */
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val deviceDefault = remember(context) { resolveDefaultCountry(context) }

    val register: AuthState.RegisterPhoneAndName =
        when (val s = state) {
            is AuthState.RegisterPhoneAndName -> s
            is AuthState.Error ->
                s.previousState as? AuthState.RegisterPhoneAndName ?: run {
                    Box(modifier = modifier.fillMaxSize())
                    return
                }
            else -> {
                Box(modifier = modifier.fillMaxSize())
                return
            }
        }

    LaunchedEffect(deviceDefault.dialCode) {
        if (register.phone.isEmpty() &&
            register.countryCode == "+1" &&
            deviceDefault.dialCode != "+1"
        ) {
            viewModel.onRegisterChanged(
                countryCode = deviceDefault.dialCode,
                phone = "",
                displayName = register.displayName,
            )
        }
    }

    val selectedCountry =
        findCountryByDialCode(register.countryCode)
            ?: deviceDefault.takeIf { it.dialCode == register.countryCode }
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
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            Text(
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Set up your Sanchr profile before verification",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Profile photo placeholder. Mirrors iOS RegisterView.profilePhotoSection.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(112.dp),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                IconButton(
                    // TODO(Phase-4): launch image picker and persist a content URI
                    //  on the AuthViewModel register state.
                    onClick = { /* no-op until Phase 4 */ },
                    modifier =
                        Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(SanchrIndigo500),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = SanchrIndigo500,
                            contentColor = SanchrWhite,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Select avatar",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            OutlinedTextField(
                value = register.displayName,
                onValueChange = { newName ->
                    viewModel.onRegisterChanged(
                        countryCode = register.countryCode,
                        phone = register.phone,
                        displayName = newName,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display Name") },
                placeholder = { Text("Enter your name") },
                singleLine = true,
                shape = SanchrShapeTokens.CornerMedium,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SanchrIndigo500,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))

            OutlinedTextField(
                value = register.phone,
                onValueChange = { newPhone ->
                    viewModel.onRegisterChanged(
                        countryCode = register.countryCode,
                        phone = newPhone,
                        displayName = register.displayName,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone Number") },
                placeholder = { Text("Phone number") },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        CountryCodePicker(
                            selected = selectedCountry,
                            onSelected = { country ->
                                viewModel.onRegisterChanged(
                                    countryCode = country.dialCode,
                                    phone = register.phone,
                                    displayName = register.displayName,
                                )
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
                                text = "We'll verify your number before creating the account",
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

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            SanchrButton(
                text = "Continue to Verification",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitRegister()
                },
                enabled =
                    register.displayName.trim().isNotEmpty() &&
                        register.phone.length >= MIN_CONTINUE_DIGITS &&
                        !register.isSubmitting,
                isLoading = register.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanchrTheme.spacing.xl),
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
        }
    }
}

private const val MIN_CONTINUE_DIGITS = 7
private const val PRIVACY_URL = "https://sanchr.app/privacy"
private const val TERMS_URL = "https://sanchr.app/terms"
