package com.sanchr.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCenteredHeader
import com.sanchr.core.designsystem.component.SanchrOtpInput
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.component.SecureScreen
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme
import kotlinx.coroutines.delay

private const val RESEND_COOLDOWN_SECONDS = 30
private const val OTP_AUTOFOCUS_DELAY_MS = 100L

/**
 * OTP verification step. Consumes [AuthViewModel.state]; renders for
 * [AuthState.OtpEntry] (and the Error-wrapped variant). Auto-submits on final
 * digit via [SanchrOtpInput.onComplete]; a separate Verify button is kept for
 * explicit confirmation / accessibility.
 *
 * iOS-parity copy synced 2026-04-25 against `OTPView.swift`. Title
 * ("Verify your number"), subtitle ("Enter the 6-digit code sent to"),
 * resend prompt ("Didn't receive the code?") and resend label ("Resend")
 * are taken character-for-character from `OTPView.swift:30-83`. The
 * resend countdown format ("Resend in 0:30") matches iOS line 69's
 * `Resend in \(viewModel.formattedCountdown)` output.
 *
 * iOS-deviation: Material `TopAppBar` carries the static title
 * "Verification" — Android navigation idiom requires a top-bar title,
 * iOS leaves the bar empty save for the `Back` chevron.
 *
 * iOS-deviation: an explicit "Verify" CTA (and its loading variant
 * "Verifying...") sits below the input. iOS auto-submits silently on the
 * 6th digit with no visible button; Android keeps the button for
 * accessibility (TalkBack users may not realise auto-submit fired) and
 * for users who paste a code mid-typing.
 *
 * iOS-parity: the soft keyboard pops automatically on entry, matching
 * `OTPView`'s `@FocusState`/`onAppear` autofocus. The [rememberSaveable]
 * `didAutoFocus` guard prevents config-change re-focus (rotation, dark-mode
 * toggle) after the user has dismissed the IME — same pattern as
 * `OnboardingNameScreen`.
 *
 * Unconditionally applies [SecureScreen] — the OTP code is sensitive and must
 * not leak via screenshots or the app-switcher thumbnail (M6 security checklist,
 * iOS parity).
 */
@Composable
fun OtpScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val otpEntry: AuthState.OtpEntry =
        when (val s = state) {
            is AuthState.OtpEntry -> s
            is AuthState.Error -> s.previousState as? AuthState.OtpEntry ?: return
            else -> return
        }
    val errorMessage = (state as? AuthState.Error)?.message

    var resendCountdown by remember { mutableIntStateOf(RESEND_COOLDOWN_SECONDS) }
    var canResend by remember { mutableStateOf(false) }

    val otpFocusRequester = remember { FocusRequester() }
    var didAutoFocus by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000L)
            resendCountdown--
        } else {
            canResend = true
        }
    }

    LaunchedEffect(Unit) {
        if (!didAutoFocus) {
            // Small delay so the IME animation does not fight the navigation transition.
            delay(OTP_AUTOFOCUS_DELAY_MS)
            try {
                otpFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Focus target not yet attached — SanchrOtpInput's tap fallback will recover.
            }
            didAutoFocus = true
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // iOS-parity centered header — handles status-bar padding internally.
        SanchrCenteredHeader(
            title = "Verification",
            onNavigateBack = onNavigateBack,
        )

        Column(
            modifier = Modifier.padding(horizontal = SanchrTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            Text(
                text = "Verify your number",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Enter the 6-digit code sent to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Text(
                text = otpEntry.phoneE164,
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrIndigo500,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            SanchrOtpInput(
                value = otpEntry.otp,
                onValueChange = viewModel::onOtpChanged,
                isError = errorMessage != null,
                focusRequester = otpFocusRequester,
                onComplete = {
                    if (!otpEntry.isSubmitting) {
                        if (state is AuthState.Error) viewModel.retry()
                        viewModel.submitOtp()
                    }
                },
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            if (!canResend) {
                Text(
                    text = "Resend in 0:${resendCountdown.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SanchrGray400,
                )
            } else {
                Text(
                    text = "Didn't receive the code?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SanchrTextButton(
                    onClick = {
                        canResend = false
                        resendCountdown = RESEND_COOLDOWN_SECONDS
                        if (state is AuthState.Error) viewModel.retry()
                        viewModel.resendOtp()
                    },
                ) {
                    Text(
                        text = "Resend",
                        style = MaterialTheme.typography.labelLarge,
                        color = SanchrIndigo500,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // iOS-deviation: iOS auto-submits on 6th digit without a button.
            // Android keeps a Verify button for accessibility and paste UX.
            SanchrButton(
                text = if (otpEntry.isSubmitting) "Verifying..." else "Verify",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitOtp()
                },
                enabled = otpEntry.otp.length == 6 && !otpEntry.isSubmitting,
                isLoading = otpEntry.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
