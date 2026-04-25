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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Onboarding entry / welcome step.
 *
 * iOS reference: `OnboardingWelcomeStepView.swift:15-130` — logo + headline +
 * value-props + primary CTA. Android deviates in two places:
 *   1. No notification-permission card inline on Welcome. On iOS this surfaces
 *      because iOS requires an explicit prompt; Android's prompt is part of the
 *      ContactSync step instead (lines up with `PermissionsScreen.kt`).
 *   2. No avatar preview / "Welcome, <name>" — on iOS this view is reused as
 *      the post-profile confirmation step. Android splits that responsibility
 *      so Welcome is purely the landing; the name/avatar preview belongs on a
 *      future post-ContactSync confirmation if we choose to add one.
 *
 * iOS-parity copy synced 2026-04-25 against `OnboardingWelcomeStepView.swift`.
 * Because Android's Welcome runs *pre*-name (vs iOS post-profile), we align
 * what we can: the E2EE value-prop body now mirrors the iOS tagline
 * (line 67) verbatim. The hero title, the other two value-prop strings, and
 * the "Get started" CTA have no direct iOS equivalent on this screen and are
 * kept as Android-idiom landing copy (see iOS-deviation comments below).
 */
@Composable
fun OnboardingWelcomeScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
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

            // iOS-deviation: iOS uses "Welcome, {name}!" only post-profile; Android's
            // Welcome runs pre-name so we use a generic landing title.
            Text(
                text = "Welcome to Sanchr",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Private, secure messaging — built for conversations that matter.",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // iOS parity: body text matches `OnboardingWelcomeStepView.swift:67` verbatim.
            ValueProp(
                icon = Icons.Filled.Lock,
                title = "End-to-end encrypted",
                body = "Your messages are end-to-end encrypted. Only you and the people you chat with can read them.",
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))
            // iOS-deviation: no equivalent on iOS Welcome; Android landing-only copy.
            ValueProp(
                icon = Icons.Filled.People,
                title = "No ads, no trackers",
                body = "We don't sell data. We don't have any to sell.",
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.lg))
            // iOS-deviation: no equivalent on iOS Welcome; Android landing-only copy.
            ValueProp(
                icon = Icons.Filled.Shield,
                title = "You're in control",
                body = "Pick a name, pick a photo, decide who to share with.",
            )

            Spacer(modifier = Modifier.weight(1f))

            // iOS-deviation: iOS CTA "Start Chatting" only fires post-profile-save.
            // Android Welcome is pre-name, so "Get started" matches the landing intent.
            SanchrButton(
                text = "Get started",
                onClick = viewModel::onWelcomeContinue,
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
