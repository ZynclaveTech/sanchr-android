package com.sanchr.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.theme.LocalSanchrSurfaces

/**
 * iOS-parity centered header: leading slot (default 40dp back chevron) +
 * centered title + trailing slot (default 40dp empty box, used for symmetry
 * or to host an action like "Skip"). Followed by a 1dp hairline divider on
 * `surfaces.line`.
 *
 * iOS reference: `ExportComponents.swift` `SanchrCenteredHeader` (lines
 * 185-225). Pixel rhythm: 16dp horizontal padding, 14dp top, 12dp bottom,
 * 40dp leading/trailing tap targets so the title visually centers between
 * symmetric slots.
 *
 * Use this in lieu of Material3 `TopAppBar` on screens that should look like
 * iOS — `TopAppBar` has its own height/title-styling that drifts from the
 * iOS look.
 */
@Composable
fun SanchrCenteredHeader(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val surfaces = LocalSanchrSurfaces.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
        ) {
            // Leading slot: caller-provided, else default back chevron, else empty 40dp.
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when {
                    leading != null -> leading()
                    onNavigateBack != null ->
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (trailing != null) trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = surfaces.line,
                thickness = 1.dp,
            )
        }
    }
}
