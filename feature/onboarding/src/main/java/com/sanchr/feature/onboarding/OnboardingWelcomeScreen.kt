package com.sanchr.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Onboarding terminal confirmation step ("YOU'RE ALL SET" on iOS).
 *
 * iOS reference: `OnboardingWelcomeStepView.swift:15-130`. Reached after
 * ContactSync; the CTA fires [OnboardingViewModel.finishOnboarding] which
 * persists the DataStore flag and emits [OnboardingState.Completed].
 *
 * Phase H5a: this file is a transitional compile-only shim. The visual
 * pixel-parity rebuild against iOS (avatar preview, "Welcome, {name}!"
 * headline, notification permission card, gradient CTA) lands in Phase H5b.
 * Until then we keep the existing landing-style body but plumb the new
 * [OnboardingState.WelcomeConfirm] state through and wire the CTA to
 * `finishOnboarding`.
 */
@Composable
fun OnboardingWelcomeScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val confirmState = state as? OnboardingState.WelcomeConfirm ?: return

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = SanchrTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.massive))

            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = SanchrIndigo100,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = SanchrIndigo500,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // H5b will replace this with the iOS "YOU'RE ALL SET" eyebrow +
            // "Welcome, {name}!" headline + avatar preview composition.
            Text(
                text = "Welcome, ${confirmState.name}!",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "You're all set. Private, secure messaging — built for conversations that matter.",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            ValueProp(
                icon = Icons.Filled.Lock,
                title = "End-to-end encrypted",
                body = "Your messages are end-to-end encrypted. Only you and the people you chat with can read them.",
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))
            ValueProp(
                icon = Icons.Filled.VisibilityOff,
                title = "No ads, no trackers",
                body = "We don't sell data. We don't have any to sell.",
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))
            ValueProp(
                icon = Icons.Filled.Tune,
                title = "You're in control",
                body = "Pick a name, pick a photo, decide who to share with.",
            )

            Spacer(modifier = Modifier.weight(1f))

            // iOS parity: CTA matches `OnboardingWelcomeStepView.swift` "Start Chatting".
            SanchrButton(
                text = "Start Chatting",
                onClick = viewModel::finishOnboarding,
                isLoading = confirmState.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = SanchrTheme.spacing.xl),
            )
        }
    }
}

@Composable
private fun ValueProp(
    icon: ImageVector,
    title: String,
    body: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = SanchrIndigo100,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SanchrIndigo500,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.size(SanchrTheme.spacing.md))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = SanchrGray900,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = SanchrGray500,
            )
        }
    }
}
