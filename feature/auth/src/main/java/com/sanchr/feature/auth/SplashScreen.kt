package com.sanchr.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import kotlinx.coroutines.delay

/**
 * Cold-launch splash screen. Android counterpart of iOS `SplashView.swift`.
 *
 * Structurally mirrors the iOS animation sequence (ambient glow, logo spring,
 * wordmark + tagline fade-up, spinner). The scene is always dark, matching the
 * iOS `preferredColorScheme(.dark)` directive, so the splash looks identical
 * in light and dark modes.
 *
 * After [SPLASH_DURATION_MS] the composable invokes [onSplashComplete] exactly
 * once. The callback is **not** wired into navigation in Phase 1 — that's
 * Phase 5 of the realignment plan.
 */
@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var appeared by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (appeared) 1.0f else 0.75f,
        animationSpec = tween(durationMillis = 450, delayMillis = 100),
        label = "splash-logo-scale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 450, delayMillis = 100),
        label = "splash-logo-alpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splash-glow-alpha",
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 500),
        label = "splash-wordmark-alpha",
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 650),
        label = "splash-tagline-alpha",
    )
    val spinnerAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = 850),
        label = "splash-spinner-alpha",
    )

    LaunchedEffect(Unit) {
        appeared = true
        delay(SPLASH_DURATION_MS)
        onSplashComplete()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SplashBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(96.dp))

            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Ambient radial glow behind the logo.
                Box(
                    modifier =
                        Modifier
                            .size(180.dp)
                            .alpha(glowAlpha)
                            .background(
                                brush =
                                    Brush.radialGradient(
                                        colors =
                                            listOf(
                                                SanchrIndigo500.copy(alpha = 0.18f),
                                                Color.Transparent,
                                            ),
                                    ),
                                shape = CircleShape,
                            ),
                )
                // Logo placeholder — tinted disc matching the primary indigo.
                // A raster `SanchrLogo` asset will drop in during Phase 6 polish;
                // the structural mass + scale animation is what matters here.
                Box(
                    modifier =
                        Modifier
                            .size(96.dp)
                            .scale(logoScale)
                            .alpha(logoAlpha)
                            .background(
                                color = SanchrIndigo500,
                                shape = CircleShape,
                            ),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Sanchr",
                color = SplashTextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(wordmarkAlpha),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Encrypted. Synced. Secure.",
                color = SplashTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.alpha(taglineAlpha),
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .alpha(spinnerAlpha)
                        .padding(bottom = 60.dp),
            ) {
                CircularProgressIndicator(color = SanchrIndigo500)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Initializing secure connection\u2026",
                    color = SplashTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// Dark splash background matches iOS `Color(hex: 0x08080E)`.
private val SplashBackground = Color(0xFF08080E)
private val SplashTextPrimary = Color(0xFFF5F5F7)
private val SplashTextSecondary = Color(0xFF9CA3AF)

private const val SPLASH_DURATION_MS = 500L
