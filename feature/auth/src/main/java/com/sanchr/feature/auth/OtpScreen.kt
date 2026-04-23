package com.sanchr.feature.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrGray400
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrShapeTokens
import com.sanchr.core.designsystem.theme.SanchrTheme
import kotlinx.coroutines.delay

@Composable
fun OtpScreen(
    phoneNumber: String,
    onVerified: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resendCountdown by remember { mutableIntStateOf(30) }
    var canResend by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the OTP input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Countdown timer for resend
    LaunchedEffect(resendCountdown) {
        if (resendCountdown > 0) {
            delay(1_000L)
            resendCountdown--
        } else {
            canResend = true
        }
    }

    // Auto-submit when all 6 digits entered
    LaunchedEffect(otpCode) {
        if (otpCode.length == 6 && !isLoading) {
            isLoading = true
            errorMessage = null
            try {
                // TODO: Call AuthServiceClient.verifyOtp() with otpCode and phoneNumber
                // val response = authServiceClient.verifyOtp(VerifyOTPRequest(phoneNumber, otpCode))
                // onVerified(response.isNewUser)
                delay(1_500L) // Placeholder delay
                onVerified(true)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Verification failed"
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Verification",
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
                text = phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrIndigo500,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // --- 6-digit OTP input ---
            BasicTextField(
                value = otpCode,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isDigit() }) {
                        otpCode = value
                        errorMessage = null
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.focusRequester(focusRequester),
                decorationBox = {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        repeat(6) { index ->
                            val char = otpCode.getOrNull(index)?.toString() ?: ""
                            OtpDigitBox(
                                digit = char,
                                isFocused = index == otpCode.length,
                                isError = errorMessage != null,
                            )
                            if (index < 5) Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                },
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // --- Countdown timer / Resend ---
            if (!canResend) {
                Text(
                    text = "Resend in 0:${resendCountdown.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SanchrGray400,
                )
            } else {
                SanchrTextButton(
                    onClick = {
                        // Reset countdown and resend
                        canResend = false
                        resendCountdown = 30
                        otpCode = ""
                        errorMessage = null
                        // TODO: Call authServiceClient.register() again to resend OTP
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
                text = if (isLoading) "Verifying..." else "Verify",
                onClick = {
                    if (otpCode.length == 6 && !isLoading) {
                        isLoading = true
                        // Auto-submit LaunchedEffect handles verification
                    }
                },
                enabled = otpCode.length == 6 && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OtpDigitBox(
    digit: String,
    isFocused: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> SanchrIndigo500
            digit.isNotEmpty() -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.outlineVariant
        }

    Surface(
        modifier =
            modifier
                .width(48.dp)
                .height(56.dp),
        shape = SanchrShapeTokens.CornerMedium,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
