package com.sanchr.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo600
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Avatar-selection step.
 *
 * iOS reference: `OnboardingAvatarStepView.swift:14-160` — tappable circle,
 * `PhotosPicker` wiring, Continue + Skip CTAs. Android deviations:
 *   - Image picker uses `ActivityResultContracts.PickVisualMedia` (the Android
 *     13+ "photo picker" with automatic fallback to `GetContent` on older
 *     devices via the androidx compat library). iOS uses PhotosUI directly.
 *   - Selected image preview uses Coil `AsyncImage` on a local URI.
 *   - No upload-on-Continue — iOS blocks advancement on `saveProfile` success;
 *     Android defers server persistence to Phase 6 (`ProfileService.UpdateProfile`
 *     wiring, see `OnboardingViewModel.swift:86-97`).
 *
 * iOS-parity copy synced 2026-04-25 against `OnboardingAvatarStepView.swift`.
 * Kicker ("STEP 2 OF 3"), title ("Add a photo"), subtitle, photo-picker
 * caption ("Tap to choose photo" / "Tap to change photo"), and "Continue"
 * CTA all match iOS verbatim (lines 29, 35, 40, 88, 134). The "Skip for now"
 * secondary action is an Android-only addition because Android defers server
 * upload until Phase 6 — see iOS-deviation comment.
 *
 * The gradient camera/pencil badge mirrors `OnboardingAvatarStepView.swift:71-84`:
 * a 38dp circle filled with a horizontal LinearGradient from `SanchrIndigo500`
 * (iOS `SanchrColors.primary` = 0x6366F1) to `SanchrIndigo600` (iOS literal
 * `Color(hex: 0x4F46E5)`), with `camera.fill` when no image is selected and
 * `pencil` (Material `Edit`) once one is.
 */
@Composable
fun OnboardingAvatarScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val avatarState = state as? OnboardingState.AvatarEntry ?: return

    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Null = user cancelled; leave state as-is.
            if (uri != null) viewModel.onAvatarSelected(uri.toString())
        }

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

            Text(
                text = "STEP 2 OF 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Text(
                text = "Add a photo",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "Help your contacts recognize you instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // Outer Box is NOT clipped so the gradient badge can extend past
            // the avatar circle on its BottomEnd corner (iOS parity —
            // `OnboardingAvatarStepView.swift:71-85`). Tap target stays the
            // full 148dp circle so the hit region matches iOS.
            Box(
                modifier = Modifier.size(148.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable {
                                pickMedia.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    val uri = avatarState.avatarUri
                    if (uri != null) {
                        AsyncImage(
                            model = uri.toUri(),
                            contentDescription = "Selected avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = SanchrIndigo100,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = SanchrIndigo500,
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }
                    }
                }

                // Gradient camera / pencil badge — 38dp circle, iOS parity
                // (`OnboardingAvatarStepView.swift:71-84`). Horizontal gradient
                // from `SanchrIndigo500` (0x6366F1) to `SanchrIndigo600`
                // (0x4F46E5) matches `[SanchrColors.primary, Color(hex: 0x4F46E5)]`.
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(SanchrIndigo500, SanchrIndigo600),
                                    ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            if (avatarState.avatarUri == null) {
                                Icons.Filled.CameraAlt
                            } else {
                                Icons.Filled.Edit
                            },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
            Text(
                text = if (avatarState.avatarUri == null) "Tap to choose photo" else "Tap to change photo",
                style = MaterialTheme.typography.bodySmall,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.weight(1f))

            SanchrButton(
                text = "Continue",
                onClick = viewModel::submitAvatar,
                enabled = avatarState.avatarUri != null && !avatarState.isSubmitting,
                isLoading = avatarState.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            // iOS-deviation: iOS has no Skip on this step (saveProfile gates Continue);
            // Android defers server persistence to Phase 6, so Skip is allowed for now.
            SanchrTextButton(
                onClick = viewModel::skipAvatar,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanchrTheme.spacing.xl),
            ) {
                Text(text = "Skip for now")
            }
        }
    }
}
