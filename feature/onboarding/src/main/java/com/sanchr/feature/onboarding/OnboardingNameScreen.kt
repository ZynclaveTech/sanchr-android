package com.sanchr.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Name-entry step.
 *
 * iOS reference: `OnboardingNameStepView.swift:8-87` — single text field,
 * "STEP 1 OF 3" kicker, helper copy, primary CTA. Android deviates:
 *   - Material 3 `OutlinedTextField` instead of a custom-rounded `TextField`
 *     (iOS uses a bespoke `SanchrExportColors.surfaceMuted` background).
 *   - Max-length cap is 128 (backend limit) instead of iOS's 40 (iOS's cap is
 *     aesthetic only — server accepts up to 128, see
 *     `sanchr-profile-service/src/proto/profile.proto`).
 */
@Composable
fun OnboardingNameScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nameState = state as? OnboardingState.NameEntry ?: return

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
                text = "STEP 1 OF 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Text(
                text = "What's your name?",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            Text(
                text = "This is how people will see you on Sanchr.",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            OutlinedTextField(
                value = nameState.name,
                onValueChange = viewModel::onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter your name") },
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = { viewModel.submitName() },
                    ),
                supportingText = {
                    Text(
                        text = "${nameState.name.length}/128",
                        style = MaterialTheme.typography.bodySmall,
                        color = SanchrGray500,
                    )
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            val canContinue = nameState.name.trim().isNotEmpty() && !nameState.isSubmitting
            SanchrButton(
                text = "Continue",
                onClick = viewModel::submitName,
                enabled = canContinue,
                isLoading = nameState.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = SanchrTheme.spacing.xl),
            )
        }
    }
}
