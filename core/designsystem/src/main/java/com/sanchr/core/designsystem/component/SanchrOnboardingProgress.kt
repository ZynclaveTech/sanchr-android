package com.sanchr.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrIndigo500

/**
 * Onboarding progress indicator: N pills (default 3), filling left-to-right
 * up to currentStep. Per `OnboardingView.swift:6-19` (capsule 34x6dp,
 * spacing 8dp, active=primary, inactive=#E5E7EB).
 */
@Composable
fun SanchrOnboardingProgress(
    currentStep: Int,
    modifier: Modifier = Modifier,
    totalSteps: Int = 3,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index < currentStep
            Box(
                modifier =
                    Modifier
                        .width(34.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (isActive) SanchrIndigo500 else Color(0xFFE5E7EB)),
            )
        }
    }
}
