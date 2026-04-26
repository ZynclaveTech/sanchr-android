package com.sanchr.feature.onboarding

import android.util.Log
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

private const val NAME_AUTOFOCUS_DELAY_MS = 100L
private const val TAG = "AuthFlow"

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
 *  - Auto-focus once per logical screen presentation matches iOS
 *    `onAppear { isNameFocused = true }` (iOS line 86). The
 *    [rememberSaveable] guard prevents configuration changes (rotation,
 *    dark-mode toggle) from re-stealing focus and re-opening the IME after
 *    the user has dismissed it. The 100ms delay + try/catch mirror
 *    `OtpScreen`'s autofocus recipe — without them an unattached
 *    [FocusRequester] (which is the case during the post-OTP nav transition,
 *    when this composable enters the tree before its TextField has been
 *    laid out) throws `IllegalStateException` and tears down the entire
 *    composition, leaving the user staring at a blank screen.
 *  - Bottom-anchored progress (step 1) + GradientButton, 36dp bottom inset.
 *
 * iOS-parity 40-character cap (iOS lines 56-60) is enforced screen-side here;
 * the ViewModel keeps its own 128-char hard cap to defend against any caller
 * bypassing this layer.
 *
 * Defensive fallback: when [OnboardingState] is unexpectedly NOT
 * [OnboardingState.NameEntry] (e.g. a transient race where the route
 * composes before the VM has emitted its initial value, or a stale state
 * left over from a previous flow), we render a fresh empty [NameEntryBody]
 * rather than `Box(fillMaxSize())`. The empty fallback was the smoking gun
 * for the post-OTP blank screen in v0.9.1 — a one-frame state miss showed
 * the user a black rectangle. The placeholder render is harmless: any
 * input that lands here is dropped because the VM rejects mutations
 * outside `NameEntry`, but the user sees the chrome instead of nothing.
 */
@Composable
fun OnboardingNameScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameState = state as? OnboardingState.NameEntry
    LaunchedEffect(state) {
        Log.d(
            TAG,
            "OnboardingNameScreen: state=${state::class.simpleName} matchedAsNameEntry=${nameState != null}",
        )
    }
    NameEntryBody(
        nameState = nameState ?: OnboardingState.NameEntry(),
        onNameChanged = viewModel::onNameChanged,
        onSubmit = viewModel::submitName,
    )
}

@Composable
private fun NameEntryBody(
    nameState: OnboardingState.NameEntry,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val gradientBrush =
        remember { Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFECFEFF))) }
    val logoCardShape = remember { RoundedCornerShape(28.dp) }
    val fieldShape = remember { RoundedCornerShape(18.dp) }
    val focusRequester = remember { FocusRequester() }
    val surfaces = LocalSanchrSurfaces.current
    var didAutoFocus by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
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
                onNameChanged(raw.take(IOS_NAME_CAP))
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
                    onDone = { if (nameState.name.isNotBlank()) onSubmit() },
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
        LaunchedEffect(Unit) {
            if (!didAutoFocus) {
                // Mirror OtpScreen autofocus: short delay so the FocusRequester
                // is attached to the TextField before requestFocus(), and
                // catch the IllegalStateException that fires when this
                // composable first enters the tree post-OTP-verify (the
                // TextField has not been laid out yet on the first frame).
                // Without this guard the exception kills the composition
                // and the user sees a blank screen.
                delay(NAME_AUTOFOCUS_DELAY_MS)
                try {
                    focusRequester.requestFocus()
                } catch (_: IllegalStateException) {
                    // Focus target not yet attached — user can tap the field manually.
                }
                didAutoFocus = true
            }
        }

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
                onClick = onSubmit,
                enabled = nameState.name.isNotBlank(),
                isLoading = nameState.isSubmitting,
                trailingIcon = null,
            )
        }
    }
}

private const val IOS_NAME_CAP = 40
