package com.sanchr.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrIndigo950

/**
 * iOS-parity gradient CTA button matching `LoginView.swift:217-250` /
 * `SanchrGradientButtonLabel` in `ExportComponents.swift:511-542`.
 *
 * Capsule shape, 64dp height, indigo->indigo-dark horizontal gradient,
 * shadow (20dp blur, 10dp y-offset, primary @ 22% alpha), scale 0.98 on press.
 *
 * Disabled state fades the whole button (gradient + shadow + content) to 0.58
 * alpha to match iOS `LoginView.swift:248-249` `.opacity(... ? 1 : 0.58)`.
 *
 * Dark end-stop maps to [SanchrIndigo950] (#4C1D95), the existing "Dark primary"
 * token in `Color.kt:18` — the plan's nominal `SanchrIndigoDark` is not a
 * separate token in this codebase.
 */
@Composable
fun SanchrGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowForward,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        label = "SanchrGradientButton.scale",
    )
    val gradientBrush =
        remember { Brush.horizontalGradient(listOf(SanchrIndigo500, SanchrIndigo950)) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.alpha(if (enabled) 1f else 0.58f)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = SanchrIndigo500.copy(alpha = 0.22f),
                    spotColor = SanchrIndigo500.copy(alpha = 0.22f),
                ).clip(CircleShape)
                .background(gradientBrush)
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                trailingIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
