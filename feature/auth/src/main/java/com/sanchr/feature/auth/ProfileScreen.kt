package com.sanchr.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextField
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWhite

/**
 * Profile-entry step: collects the user's display name prior to OTP dispatch.
 *
 * Under the revised M4 topology this step precedes OTP — submitting here issues
 * the `Register` RPC which generates the OTP. Avatar + bio fields remain as
 * placeholders; they aren't wired into the AuthViewModel contract yet.
 */
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val profileEntry: AuthState.ProfileEntry =
        when (val s = state) {
            is AuthState.ProfileEntry -> s
            is AuthState.Error -> s.previousState as? AuthState.ProfileEntry ?: return
            else -> return
        }
    val errorMessage = (state as? AuthState.Error)?.message

    Scaffold(
        topBar = {
            SanchrTopBar(title = "Create Profile", onNavigateBack = onNavigateBack)
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SanchrTheme.spacing.xl)
                    .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            // Avatar placeholder — image picker wiring arrives in a later milestone.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(112.dp),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { /* TODO: Launch image picker for avatar */ },
                    modifier =
                        Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(SanchrIndigo500),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = SanchrIndigo500,
                            contentColor = SanchrWhite,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Select avatar",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            SanchrTextField(
                value = profileEntry.displayName,
                onValueChange = viewModel::onDisplayNameChanged,
                label = "Display Name",
                placeholder = "Your name",
                isError = errorMessage != null,
                errorMessage = errorMessage,
                enabled = !profileEntry.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            SanchrButton(
                text = if (profileEntry.isSubmitting) "Sending..." else "Send code",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitProfile()
                },
                enabled = !profileEntry.isSubmitting && profileEntry.displayName.isNotBlank(),
                isLoading = profileEntry.isSubmitting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.default))

            Text(
                text = "You can change your profile details later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}
