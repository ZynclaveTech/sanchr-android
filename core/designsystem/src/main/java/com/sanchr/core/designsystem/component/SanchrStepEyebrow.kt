package com.sanchr.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrMicroEyebrowStyle

/**
 * "STEP X OF Y" / "YOU'RE ALL SET" eyebrow text style.
 * Per `OnboardingNameStepView.swift:29-32`,
 * `OnboardingAvatarStepView.swift:29-32`, and
 * `OnboardingWelcomeStepView.swift:34-37`.
 *
 * 10sp Afacad Normal, 2.5sp letterSpacing, primary color.
 */
@Composable
fun SanchrStepEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = SanchrMicroEyebrowStyle,
        color = SanchrIndigo500,
        modifier = modifier,
    )
}
