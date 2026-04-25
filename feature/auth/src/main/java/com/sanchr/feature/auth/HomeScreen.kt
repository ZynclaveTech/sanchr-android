package com.sanchr.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Unified Login / Register landing. Mirrors iOS `LoginView` hero + dual CTA
 * pattern (LoginView.swift:70-95) while adding an explicit "Sign up" path
 * Android lacked before the realignment. Tapping "Log in" transitions to
 * [AuthState.LoginPhone]; tapping "Sign up" transitions to
 * [AuthState.RegisterPhoneAndName].
 *
 * iOS-parity copy synced 2026-04-25 against `LoginView.swift`. The hero
 * title ("Welcome to Sanchr") and tagline ("Encrypted. Synced. Secure.")
 * are taken character-for-character from `LoginView.swift:81-89`. The logo
 * uses the raster `sanchr_logo` asset sized 120dp to match
 * `LoginView.swift:72-75`.
 *
 * iOS-deviation: iOS has no separate "Home" landing — `LoginView` is both
 * the hero and the phone-entry screen, and there is no explicit "Sign up"
 * affordance (registration is implicit on first OTP verification). Android
 * splits the two; the "Log in" / "New to Sanchr?" / "Sign up" copy is
 * Android-only and has no iOS counterpart to mirror.
 *
 * On first composition this screen fires [AuthViewModel.attemptFastLogin].
 * When the device has cached credentials the VM transitions straight to
 * [AuthState.Done] and `AuthFlowHost` navigates out before the user ever
 * sees the chooser. Failures are silent — the user just sees this screen.
 *
 * TODO(copy): Privacy Policy / Terms URLs below use the placeholder
 *   `sanchr.app/privacy` and `sanchr.app/terms`. Replace with the final
 *   marketing URLs once legal sign-off lands.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.attemptFastLogin()
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SanchrTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.massive))

            // Raster SanchrLogo asset — 120dp to match `LoginView.swift:75`.
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = com.sanchr.core.designsystem.R.drawable.sanchr_logo),
                    contentDescription = "Sanchr",
                    modifier = Modifier.size(120.dp),
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

            Spacer(modifier = Modifier.weight(1f))

            // iOS-deviation: dual-CTA chooser is Android-only; iOS LoginView
            // merges hero + phone entry into a single screen.
            SanchrButton(
                text = "Log in",
                onClick = { viewModel.chooseLogin() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New to Sanchr?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SanchrGray500,
                )
                SanchrTextButton(onClick = { viewModel.chooseRegister() }) {
                    Text(
                        text = "Sign up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SanchrIndigo500,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // iOS-deviation: iOS LoginView shows a single combined sentence
            // ("By continuing, you agree to our Privacy Policy and Terms of
            // Service") whereas Android exposes the two documents as
            // separate tappable links — Material idiom for legal footers.
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

private const val PRIVACY_URL = "https://sanchr.app/privacy"
private const val TERMS_URL = "https://sanchr.app/terms"
