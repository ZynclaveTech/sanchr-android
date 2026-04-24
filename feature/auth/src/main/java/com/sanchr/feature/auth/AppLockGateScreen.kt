package com.sanchr.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stub app-lock gate. Android counterpart of iOS `AppLockGateView.swift`.
 *
 * **Phase 1 behaviour:** this screen is a visual stub. It unconditionally
 * invokes [onUnlocked] on first composition so any navigation wiring that
 * routes through it in a future phase does not block the user. A visible
 * "Unlock" button is also wired to [onUnlocked] for manual dismissal.
 *
 * The real `androidx.biometric.BiometricPrompt` integration lands in Phase 6
 * alongside the `SettingsRepository` lock-enabled toggle (see
 * `docs/android/auth-onboarding-realignment-plan.md` §7).
 */
@Composable
fun AppLockGateScreen(onUnlocked: () -> Unit) {
    // TODO(Phase-6): wire BiometricPrompt via androidx.biometric and only call
    //  onUnlocked on a successful `BiometricPrompt.AuthenticationCallback
    //  .onAuthenticationSucceeded`. See iOS AppLockGateView.swift:57-87 for the
    //  reference `LAContext.evaluatePolicy` flow.
    LaunchedEffect(Unit) {
        onUnlocked()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Text(
                text = "App Locked",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Button(
                onClick = onUnlocked,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
            ) {
                Text(
                    text = "Unlock",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
