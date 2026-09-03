package com.sanchr.feature.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrOutlinedButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

private data class AuthPermissionSpec(
    val manifest: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

/**
 * Build the list of runtime permissions we request during onboarding. POST_NOTIFICATIONS
 * is only declared/requested on API 33+; on older devices the grant is implicit and we
 * omit the row so we don't confuse the user with an un-requestable toggle.
 */
private fun buildPermissionSpecs(): List<AuthPermissionSpec> =
    buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                AuthPermissionSpec(
                    manifest = Manifest.permission.POST_NOTIFICATIONS,
                    title = "Notifications",
                    description = "So you hear about messages and calls when the app is closed.",
                    icon = Icons.Filled.Notifications,
                ),
            )
        }
        add(
            AuthPermissionSpec(
                manifest = Manifest.permission.READ_CONTACTS,
                title = "Contacts",
                description = "So Sanchr can tell you which of your contacts are already here.",
                icon = Icons.Filled.Contacts,
            ),
        )
    }

/**
 * Permissions step. Requests POST_NOTIFICATIONS (API 33+) and READ_CONTACTS and
 * reports the outcome to [AuthViewModel.onPermissionsResult]. Submit is always
 * enabled — mirroring the iOS flow, we let the user continue even if they deny,
 * and ask again contextually later.
 */
@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Render for the Permissions step, its Registering overlay, or its Error
    // wrapper; bail otherwise so the NavHost can move us elsewhere.
    when (val s = state) {
        is AuthState.Permissions -> Unit
        is AuthState.Registering -> s.context // overlay renders on top of us
        is AuthState.Error -> s.previousState as? AuthState.Permissions ?: return
        else -> return
    }

    val context = LocalContext.current
    val specs = remember { buildPermissionSpecs() }

    // Seed grant-state from the system so rows reflect prior grants on re-entry.
    val grantMap =
        remember {
            mutableStateMapOf<String, Boolean>().apply {
                specs.forEach { spec ->
                    put(
                        spec.manifest,
                        ContextCompat.checkSelfPermission(context, spec.manifest) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }
            }
        }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            result.forEach { (perm, granted) -> grantMap[perm] = granted }
            val grantedSet = grantMap.filterValues { it }.keys
            viewModel.onPermissionsResult(grantedSet)
        }

    val unrequested = specs.map { it.manifest }.filter { grantMap[it] != true }
    val errorMessage = (state as? AuthState.Error)?.message
    val isBusy = state is AuthState.Registering

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SanchrTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.massive))

            Text(
                text = "One more step",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))

            Text(
                text = "Sanchr works best with a couple of permissions. You can always change these later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))

            specs.forEach { spec ->
                PermissionRow(
                    spec = spec,
                    granted = grantMap[spec.manifest] == true,
                    onGrant = { launcher.launch(arrayOf(spec.manifest)) },
                )
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (unrequested.isNotEmpty()) {
                SanchrOutlinedButton(
                    onClick = { launcher.launch(unrequested.toTypedArray()) },
                    enabled = !isBusy,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Text(text = "Grant all")
                }
                Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            }

            SanchrButton(
                text = "Continue",
                onClick = {
                    if (state is AuthState.Error) viewModel.retry()
                    viewModel.submitPermissions()
                },
                enabled = !isBusy,
                isLoading = isBusy,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(bottom = SanchrTheme.spacing.xl),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    spec: AuthPermissionSpec,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SanchrIndigo100),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                tint = SanchrIndigo500,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.size(SanchrTheme.spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.labelLarge,
                color = SanchrGray900,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = SanchrGray500,
            )
        }

        Spacer(modifier = Modifier.size(SanchrTheme.spacing.md))

        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            SanchrOutlinedButton(
                onClick = onGrant,
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
