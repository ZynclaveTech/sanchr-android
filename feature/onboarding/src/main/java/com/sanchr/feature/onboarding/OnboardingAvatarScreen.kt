package com.sanchr.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.sanchr.core.designsystem.component.SanchrGradientButton
import com.sanchr.core.designsystem.component.SanchrOnboardingProgress
import com.sanchr.core.designsystem.component.SanchrStepEyebrow
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo600

/**
 * Avatar-selection step (Phase H6 iOS pixel-parity rebuild).
 *
 * iOS reference: `OnboardingAvatarStepView.swift` (full file). Layout rhythm
 * matches `:feature:onboarding` spec §6.3:
 *   - 28dp top breath after the chevron-back row.
 *   - SanchrStepEyebrow("STEP 2 OF 3") -> 12dp -> displayMedium title ->
 *     8dp -> body subtitle -> 30dp -> 148dp avatar dropzone.
 *   - 14dp under the dropzone -> "Tap to choose / change photo" caption.
 *   - Bottom column: SanchrOnboardingProgress(2) + SanchrGradientButton,
 *     spaced 18dp, 36dp bottom padding.
 *
 * Dropzone (empty state): a 148dp circle filled with [LocalSanchrSurfaces]
 * `surfaceSoft` and outlined with a 2dp dashed stroke (`[8,6]` on/off,
 * primary tint), centered 40dp `+` glyph. The dashed stroke is rendered by
 * [dashedCircleBorder] using `Modifier.drawBehind` + `PathEffect.dashPathEffect`,
 * mirroring the iOS `Circle().stroke(style: StrokeStyle(lineWidth: 2,
 * dash: [8, 6]))` recipe.
 *
 * Dropzone (filled state): Coil [SubcomposeAsyncImage] cropped to a circle,
 * with [AvatarPlaceholder] as both loading + error fallback so a missing
 * file or a slow load never shows a blank gap.
 *
 * Gradient camera/pencil badge (38dp, BottomEnd): horizontal gradient from
 * [SanchrIndigo500] (#6366F1) to [SanchrIndigo600] (#4F46E5) — matches iOS
 * `[SanchrColors.primary, Color(hex: 0x4F46E5)]`. Icon switches between
 * [Icons.Filled.PhotoCamera] (no avatar) and [Icons.Filled.Edit] (avatar
 * picked), exactly as iOS toggles between `camera.fill` and `pencil`.
 *
 * Back behaviour: chevron-back IconButton calls [OnboardingViewModel.back],
 * which transitions AvatarEntry -> NameEntry (preserving the typed name).
 * iOS has no Skip on this step; we match — only chevron + Continue.
 */
@Composable
fun OnboardingAvatarScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val avatarState = state as? OnboardingState.AvatarEntry ?: return

    val photoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Null = user cancelled; leave state as-is.
            if (uri != null) viewModel.onAvatarSelected(uri.toString())
        }

    val avatarShape = remember { CircleShape }
    val badgeGradient =
        remember { Brush.horizontalGradient(listOf(SanchrIndigo500, SanchrIndigo600)) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
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

        Spacer(Modifier.height(28.dp))
        SanchrStepEyebrow(text = "STEP 2 OF 3")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add a photo",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Help your contacts recognize you instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(30.dp))

        // 148dp avatar dropzone with dashed-stroke placeholder + gradient badge.
        // The outer Box is the tap target; the badge is positioned BottomEnd.
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier =
                Modifier
                    .size(148.dp)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
        ) {
            val uri = avatarState.avatarUri
            if (uri != null) {
                SubcomposeAsyncImage(
                    model = uri.toUri(),
                    contentDescription = "Selected profile photo",
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(148.dp)
                            .clip(avatarShape),
                    error = { AvatarPlaceholder(modifier = Modifier.size(148.dp)) },
                    loading = { AvatarPlaceholder(modifier = Modifier.size(148.dp)) },
                )
            } else {
                AvatarPlaceholder(modifier = Modifier.size(148.dp))
            }

            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(avatarShape)
                        .background(badgeGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (avatarState.avatarUri == null) {
                            Icons.Filled.PhotoCamera
                        } else {
                            Icons.Filled.Edit
                        },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text =
                if (avatarState.avatarUri == null) {
                    "Tap to choose photo"
                } else {
                    "Tap to change photo"
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
        ) {
            SanchrOnboardingProgress(currentStep = 2)
            // TODO: error surface when AvatarEntry exposes errorMessage
            // (server-side avatar upload lands in a later phase, see
            // OnboardingViewModel.swift:86-97). Today there is no failure
            // path for this step on Android.
            SanchrGradientButton(
                text = "Continue",
                onClick = viewModel::submitAvatar,
                isLoading = avatarState.isSubmitting,
                trailingIcon = null,
            )
        }
    }
}

/**
 * Empty-state avatar placeholder: surfaceSoft fill + 2dp dashed indigo
 * border + centered 40dp `+` icon. Mirrors iOS `OnboardingAvatarStepView`
 * empty-state circle, spec §6.3.
 */
@Composable
private fun AvatarPlaceholder(modifier: Modifier = Modifier) {
    val surfaces = LocalSanchrSurfaces.current
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(surfaces.surfaceSoft)
                .dashedCircleBorder(
                    stroke = 2.dp,
                    color = SanchrIndigo500,
                    dashOn = 8.dp,
                    dashOff = 6.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = SanchrIndigo500,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * Draws a dashed-stroke circle inside the composable's layout box, inset by
 * half the stroke width so the stroke sits flush with the bounds (no
 * clipping). Spec §6.3 recipe.
 */
private fun Modifier.dashedCircleBorder(
    stroke: Dp,
    color: Color,
    dashOn: Dp,
    dashOff: Dp,
): Modifier =
    this.drawBehind {
        val strokePx = stroke.toPx()
        val dashEffect =
            PathEffect.dashPathEffect(
                floatArrayOf(dashOn.toPx(), dashOff.toPx()),
                0f,
            )
        drawCircle(
            color = color,
            radius = (size.minDimension - strokePx) / 2f,
            style =
                Stroke(
                    width = strokePx,
                    pathEffect = dashEffect,
                ),
        )
    }
