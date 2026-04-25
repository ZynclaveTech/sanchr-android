package com.sanchr.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrGradientButton
import com.sanchr.core.designsystem.component.SanchrOnboardingProgress
import com.sanchr.core.designsystem.component.SanchrStepEyebrow
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces
import com.sanchr.core.designsystem.theme.SanchrCyan500
import com.sanchr.core.designsystem.theme.SanchrIndigo500

/**
 * Phase H5b — pixel-parity rebuild against
 * `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingWelcomeStepView.swift`
 * (full file, lines 1-170).
 *
 * Top-level rhythm:
 *  - Leading chevron-back row (iOS lines 18-29) wired to
 *    [OnboardingViewModel.back].
 *  - Centered "YOU'RE ALL SET" eyebrow (iOS lines 34-37).
 *  - 96dp circular avatar (iOS lines 41-58): `AsyncImage` if
 *    [OnboardingState.WelcomeConfirm.avatarUri] is set, otherwise a
 *    [SanchrIndigo500] -> [SanchrCyan500] gradient fallback with the first
 *    letter of the name.
 *  - displaySmall "Welcome, {name}!" + bodySmall E2EE tagline pinned to a
 *    260dp max width (iOS line 71).
 *  - Conditional [NotificationCard] — only rendered while
 *    [OnboardingState.WelcomeConfirm.notificationsEnabled] is false (iOS lines
 *    74-79). The "Enable" button launches [Manifest.permission.POST_NOTIFICATIONS]
 *    on API 33+ via [rememberLauncherForActivityResult] and reports the result
 *    back through [OnboardingViewModel.notificationGranted]. Pre-Tiramisu the
 *    permission is implicitly granted, so the initial `LaunchedEffect` flips
 *    the flag to `true` and the card never renders.
 *  - Bottom-anchored stack: [SanchrOnboardingProgress] (currentStep = 3),
 *    optional inline error, and the gradient CTA. CTA label flips between
 *    "Start Chatting" and "Retry" based on
 *    [OnboardingState.WelcomeConfirm.errorMessage] (iOS line 111).
 */
@Composable
fun OnboardingWelcomeScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val welcomeState = state as? OnboardingState.WelcomeConfirm
    if (welcomeState == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val notifLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> viewModel.notificationGranted(granted) },
        )

    LaunchedEffect(Unit) {
        val initialEnabled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Pre-Tiramisu: POST_NOTIFICATIONS is implicitly granted.
                true
            }
        viewModel.notificationGranted(initialEnabled)
    }

    val onRequestPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Tiramisu: already granted; the initial LaunchedEffect set the
            // flag and the card is hidden, so this branch is unreachable in
            // practice. Kept as a defensive no-op.
            viewModel.notificationGranted(true)
        }
    }

    val gradientBrush = remember { Brush.linearGradient(listOf(SanchrIndigo500, SanchrCyan500)) }
    val avatarShape = remember { CircleShape }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
        ) {
            IconButton(onClick = viewModel::back) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        SanchrStepEyebrow("YOU'RE ALL SET")
        Spacer(Modifier.height(16.dp))

        if (welcomeState.avatarUri != null) {
            AsyncImage(
                model = welcomeState.avatarUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(avatarShape),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(avatarShape)
                        .background(gradientBrush),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        welcomeState.name
                            .firstOrNull()
                            ?.uppercase()
                            ?: "",
                    style =
                        MaterialTheme.typography.displayLarge.copy(color = Color.White),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Welcome, ${welcomeState.name}!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                "Your messages are end-to-end encrypted. Only you and the people you chat with can read them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .widthIn(max = 260.dp)
                    .padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(48.dp))

        if (!welcomeState.notificationsEnabled) {
            NotificationCard(
                onEnable = onRequestPermission,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp),
        ) {
            SanchrOnboardingProgress(currentStep = 3)
            welcomeState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            SanchrGradientButton(
                text = if (welcomeState.errorMessage != null) "Retry" else "Start Chatting",
                onClick = viewModel::finishOnboarding,
                isLoading = welcomeState.isSubmitting,
                trailingIcon = null,
            )
        }
    }
}

/**
 * Inline notification permission card (iOS lines 134-168). 16dp rounded
 * surface card with leading [Icons.Filled.NotificationsActive] tinted
 * [SanchrIndigo500], two-line copy, and a trailing 8dp-rounded primary pill
 * "Enable" button that delegates to [onEnable].
 */
@Composable
private fun NotificationCard(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = remember { RoundedCornerShape(16.dp) }
    val pillShape = remember { RoundedCornerShape(8.dp) }
    val surfaces = LocalSanchrSurfaces.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(surfaces.surface)
                .padding(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = SanchrIndigo500,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Enable Notifications",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Know when you receive messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .clip(pillShape)
                    .background(SanchrIndigo500)
                    .clickable(onClick = onEnable)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Enable",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}
