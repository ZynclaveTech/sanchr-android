package com.sanchr.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrOtpInput
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme
import kotlinx.coroutines.delay

private const val RESEND_COOLDOWN_SECONDS = 30

/**
 * OTP verification step. Consumes [AuthViewModel.state]; renders for
 * [AuthState.OtpEntry] (and the Error-wrapped variant). Auto-submits on final
 * digit via [SanchrOtpInput.onComplete]; a separate Verify button is kept for
 * explicit confirmation / accessibility.
 */
@Composable
fun OtpScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
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

    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000L)
            resendCountdown--
        } else {
            canResend = true
        }
    }

    Scaffold(
        topBar = {
            SanchrTopBar(title = "Verification", onNavigateBack = onNavigateBack)
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = SanchrTheme.spacing.xl)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            Text(
                text = "Enter the 6-digit code",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "We sent a verification code to",
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
                SanchrTextButton(
                    onClick = {
                        canResend = false
                        resendCountdown = RESEND_COOLDOWN_SECONDS
                        if (state is AuthState.Error) viewModel.retry()
                        viewModel.resendOtp()
                    },
                ) {
                    Text(
                        text = "Didn't receive? Resend code",
                        style = MaterialTheme.typography.labelLarge,
                        color = SanchrIndigo500,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

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
