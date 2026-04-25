package com.sanchr.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.R
import com.sanchr.core.designsystem.component.SanchrGradientButton
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces
import com.sanchr.core.designsystem.theme.SanchrCyan500
import com.sanchr.core.designsystem.theme.SanchrIndigo500

/**
 * Contact-sync step (Phase H7 — pixel-parity rebuild).
 *
 * iOS reference: `OnboardingContactSyncStepView.swift` (delegates to
 * `ContactSyncView`). Layout mirrors `ContactSyncView.swift:76-161`
 * (`permissionRequestView` + `permissionCard`).
 *
 * Permission set: `READ_CONTACTS` only — iOS-parity. `POST_NOTIFICATIONS`
 * is owned by the Welcome screen (Phase H5b), matching iOS where
 * `ContactSyncView` requests Contacts only and notifications are requested
 * separately on the post-ContactSync welcome step.
 *
 * Sync UI (in-flight progress, "Syncing\u2026" label, sync-result list) is
 * deferred per spec §1 non-goals; will land with the actual contact-discovery
 * RPC. The [OnboardingState.ContactSync.isSubmitting] field is retained so
 * the VM's `back()` no-op-while-submitting guard remains a meaningful
 * contract for that future flow.
 *
 * iOS-bug-compat: per-card `tint` argument is accepted by `permissionCard`
 * but the icon foreground is hardcoded to `.sanchrPrimary` (see
 * `ContactSyncView.swift:142`). We replicate that — every card icon renders
 * indigo regardless of the conceptual tint. Cited in spec §6.4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingContactSyncScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncState =
        state as? OnboardingState.ContactSync ?: run {
            Box(modifier.fillMaxSize())
            return
        }

    val avatarLogoShape = remember { RoundedCornerShape(28.dp) }

    val contactsPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { _ ->
                // iOS-deviation: iOS runs an in-app sync flow with progress + results UI
                // after permission grant (`ContactSyncView.syncContacts`). Android defers
                // that — for now grant-or-deny we always advance, matching the iOS UX
                // where "Sync All Contacts" requests the permission then advances.
                viewModel.onContactSyncFinish()
            },
        )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sync Contacts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::onContactSyncFinish) {
                        Text(
                            text = "Skip",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = SanchrIndigo500,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 28.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))

            // Hero: 120dp logo with 40dp cyan sync badge offset (8,8) BottomEnd.
            // iOS: `ContactSyncView.swift:80-96`.
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(
                    painter = painterResource(R.drawable.sanchr_logo),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(avatarLogoShape),
                )
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .offset(x = 8.dp, y = 8.dp)
                            .clip(CircleShape)
                            .background(SanchrCyan500),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // iOS parity: `ContactSyncView.swift:99` ("Find Your Friends").
            Text(
                text = "Find Your Friends",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            // iOS parity: `ContactSyncView.swift:103-105` verbatim.
            Text(
                text =
                    "Sync your contacts to see who's already on Sanchr and start secure conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Three permission cards verbatim from `ContactSyncView.swift:111-131`.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PermissionCard(
                    icon = Icons.Filled.Shield,
                    title = "Private & Secure",
                    subtitle =
                        "Your contacts are encrypted and never shared with third parties",
                )
                PermissionCard(
                    icon = Icons.Filled.VerifiedUser,
                    title = "Instant Matching",
                    subtitle = "Automatically find friends who are already using Sanchr",
                )
                PermissionCard(
                    icon = Icons.Filled.PanTool,
                    title = "No Spam, Ever",
                    subtitle =
                        "We won't send notifications to your contacts without your permission",
                )
            }

            Spacer(Modifier.height(24.dp))

            SanchrGradientButton(
                text = "Sync All Contacts",
                onClick = {
                    contactsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                },
                isLoading = false,
                trailingIcon = null,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Icon-on-tinted-square card row. iOS reference:
 * `ContactSyncView.swift:138-165`. Spec §6.4 `permissionCard` recipe:
 *   - Outer: `RoundedCornerShape(20.dp)` on `surfaces.surface`, padding 16.dp.
 *   - Icon container: 44.dp `RoundedCornerShape(14.dp)` on `surfaces.surfaceMuted`.
 *   - Icon: 18.dp, tint hardcoded `SanchrIndigo500` (iOS bug-compat — see file header).
 *   - Title: `labelLarge` on `onBackground`.
 *   - Subtitle: `bodySmall` on `onSurfaceVariant`, 2.dp gap.
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val cardShape = remember { RoundedCornerShape(20.dp) }
    val iconBoxShape = remember { RoundedCornerShape(14.dp) }
    val surfaces = LocalSanchrSurfaces.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(surfaces.surface)
                .padding(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(iconBoxShape)
                    .background(surfaces.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SanchrIndigo500,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
