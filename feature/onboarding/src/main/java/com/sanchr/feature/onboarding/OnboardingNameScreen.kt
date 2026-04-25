package com.sanchr.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.R
import com.sanchr.core.designsystem.component.SanchrGradientButton
import com.sanchr.core.designsystem.component.SanchrOnboardingProgress
import com.sanchr.core.designsystem.component.SanchrStepEyebrow
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces

/**
 * Phase H5b — pixel-parity rebuild against
 * `ios/Sanchr-iOS/Features/Onboarding/Presentation/OnboardingNameStepView.swift`
 * (full file, lines 1-88).
 *
 * Layout rhythm (top -> bottom):
 *  - 44dp top spacer.
 *  - 108dp rounded-28 logo card filled with the indigo50 -> cyan50 linear
 *    gradient, 64dp logo image centered (iOS lines 12-26).
 *  - 28dp gap, "STEP 1 OF 3" eyebrow via [SanchrStepEyebrow] (iOS lines 29-32).
 *  - 12dp gap, displayMedium title "What's your name?" (iOS lines 35-38).
 *  - 8dp gap, bodyMedium subtitle, centered, 40dp horizontal padding
 *    (iOS lines 40-44).
 *  - 30dp gap, 56dp height TextField on `surfaceMuted` with rounded-18 corners
 *    (iOS lines 47-66). Capitalization=Words, IME=Done.
 *  - Auto-focus on first composition matches `onAppear { isNameFocused = true }`
 *    (iOS line 86).
 *  - Bottom-anchored progress (step 1) + GradientButton, 36dp bottom inset.
 *
 * iOS-parity 40-character cap (iOS lines 56-60) is enforced screen-side here;
 * the ViewModel keeps its own 128-char hard cap to defend against any caller
 * bypassing this layer.
 */
@Composable
fun OnboardingNameScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameState = state as? OnboardingState.NameEntry
    if (nameState == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val gradientBrush =
        remember { Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFECFEFF))) }
    val logoCardShape = remember { RoundedCornerShape(28.dp) }
    val fieldShape = remember { RoundedCornerShape(18.dp) }
    val focusRequester = remember { FocusRequester() }
    val surfaces = LocalSanchrSurfaces.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
    ) {
        Spacer(Modifier.height(44.dp))

        Box(
            modifier =
                Modifier
                    .size(108.dp)
                    .clip(logoCardShape)
                    .background(gradientBrush),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.sanchr_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(64.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        SanchrStepEyebrow("STEP 1 OF 3")
        Spacer(Modifier.height(12.dp))

        Text(
            text = "What's your name?",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This is how people will see you on Sanchr.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )

        Spacer(Modifier.height(30.dp))

        TextField(
            value = nameState.name,
            onValueChange = { raw ->
                // iOS lines 56-60 cap input at 40 chars (aesthetic cap).
                viewModel.onNameChanged(raw.take(IOS_NAME_CAP))
            },
            placeholder = {
                Text(
                    text =
                        if (nameState.prefilledName.isNotBlank()) {
                            nameState.prefilledName
                        } else {
                            "Enter your name"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { if (nameState.name.isNotBlank()) viewModel.submitName() },
                ),
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
            colors =
                TextFieldDefaults.colors(
                    unfocusedContainerColor = surfaces.surfaceMuted,
                    focusedContainerColor = surfaces.surfaceMuted,
                    disabledContainerColor = surfaces.surfaceMuted,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            shape = fieldShape,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(56.dp)
                    .focusRequester(focusRequester),
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
            SanchrOnboardingProgress(currentStep = 1)
            SanchrGradientButton(
                text = "Continue",
                onClick = viewModel::submitName,
                enabled = nameState.name.isNotBlank(),
                isLoading = nameState.isSubmitting,
                trailingIcon = null,
            )
        }
    }
}

private const val IOS_NAME_CAP = 40
