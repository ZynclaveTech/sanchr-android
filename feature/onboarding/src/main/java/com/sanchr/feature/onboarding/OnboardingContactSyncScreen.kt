package com.sanchr.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrTextButton
import com.sanchr.core.designsystem.theme.SanchrGray500
import com.sanchr.core.designsystem.theme.SanchrGray900
import com.sanchr.core.designsystem.theme.SanchrIndigo100
import com.sanchr.core.designsystem.theme.SanchrIndigo500
import com.sanchr.core.designsystem.theme.SanchrTheme

/**
 * Contact-sync / notification-permission step.
 *
 * iOS reference: `OnboardingContactSyncStepView.swift:4-22` (wraps
 * `ContactSyncView`). Android reimplements the permission-request pattern
 * from `feature/auth/.../PermissionsScreen.kt:124-127` — we replicate the
 * `rememberLauncherForActivityResult(RequestMultiplePermissions())` shape
 * rather than import from `:feature:auth`, because `:feature:onboarding` must
 * stay decoupled (see realignment plan §3 and §8 OPEN-Q#4).
 *
 * Permission set:
 *   - `READ_CONTACTS` (always)
 *   - `POST_NOTIFICATIONS` (API 33+; implicit grant on older)
 *
 * User behaviour matches iOS: granting is optional. On either grant or deny,
 * we advance to [OnboardingState.Completed]. Declined permissions are handled
 * contextually later (future Settings surfaces).
 *
 * iOS-parity copy synced 2026-04-25 against `ContactSyncView.swift`
 * (the iOS step view itself just wraps that screen). Title ("Find Your
 * Friends", line 99), subtitle (line 103-105), primary CTA ("Sync All
 * Contacts", line 274), and secondary action ("Skip", line 38) now match
 * iOS verbatim. Sync-progress / sync-complete states from iOS
 * (`syncProgressView`, `syncResultsView`) are deferred — Android currently
 * fires the OS permission dialog and advances on any result, regardless of
 * grant outcome (see iOS-deviation comment on the launcher).
 */
@Composable
fun OnboardingContactSyncScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val requestedPermissions =
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            // iOS-deviation: iOS runs an in-app sync flow with progress + results UI
            // after permission grant (`ContactSyncView.syncContacts`). Android defers
            // that to Phase 7 — for now grant or deny, we always move on.
            viewModel.onContactSyncFinish()
        }

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
                text = "STEP 3 OF 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = SanchrIndigo100,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = SanchrIndigo500,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // iOS parity: matches `ContactSyncView.swift:99` verbatim (title-case).
            Text(
                text = "Find Your Friends",
                style = MaterialTheme.typography.headlineMedium,
                color = SanchrGray900,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            // iOS parity: matches `ContactSyncView.swift:103-105` verbatim.
            Text(
                text = "Sync your contacts to see who's already on Sanchr and start secure conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = SanchrGray500,
            )

            Spacer(modifier = Modifier.weight(1f))

            // iOS parity: primary CTA matches `ContactSyncView.swift:274` ("Sync All Contacts").
            SanchrButton(
                text = "Sync All Contacts",
                onClick = { launcher.launch(requestedPermissions) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
            )
            Spacer(modifier = Modifier.height(SanchrTheme.spacing.sm))
            // iOS parity: secondary action matches `ContactSyncView.swift:38` ("Skip").
            // iOS-deviation: iOS positions Skip in the nav-bar trailing slot; Android
            // uses an inline TextButton because Compose Scaffold here has no top bar.
            SanchrTextButton(
                onClick = viewModel::onContactSyncFinish,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanchrTheme.spacing.xl),
            ) {
                Text(text = "Skip")
            }
        }
    }
}
