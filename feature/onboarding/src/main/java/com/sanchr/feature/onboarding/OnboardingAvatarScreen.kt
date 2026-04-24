package com.sanchr.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
 *   - No gradient "camera/pencil" overlay badge — that's pure visual polish,
 *     deferred to Phase 6 (design-system pass).
 *   - No upload-on-Continue — iOS blocks advancement on `saveProfile` success;
 *     Android defers server persistence to Phase 6 (`ProfileService.UpdateProfile`
 *     wiring, see `OnboardingViewModel.swift:86-97`).
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

            Box(
                modifier =
                    Modifier
                        .size(148.dp)
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
