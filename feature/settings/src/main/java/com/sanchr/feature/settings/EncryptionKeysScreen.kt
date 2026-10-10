package com.sanchr.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncryptionKeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EncryptionKeysViewModel = hiltViewModel(),
) {
    val fingerprintBlocks by viewModel.fingerprintBlocks.collectAsStateWithLifecycle()
    val keyState by viewModel.keyState.collectAsStateWithLifecycle()
    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SanchrTopBar(
                title = "Encryption Keys",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
        ) {
            // Identity Fingerprint section
            Text(
                text = "Your Identity Fingerprint",
                style = IosType.bodyBold,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                    Text(
                        text = "Safety Number Fingerprint",
                        style = IosType.captionSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                    if (fingerprintBlocks.isEmpty()) {
                        Text(
                            text = "Your key isn't available yet.",
                            style = IosType.captionSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
                    ) {
                        fingerprintBlocks.forEach { block ->
                            Box(
                                modifier =
                                    Modifier
                                        .padding(horizontal = SanchrTheme.spacing.xs)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = block.uppercase(),
                                    style =
                                        IosType.body.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Per-contact verification lives in each conversation. A safety
            // number is computed from two identity keys, so this account-level
            // screen has no second key to pair with and cannot show one. It
            // used to draw a QR icon over a grey square above a button that did
            // nothing; pointing at the screen that does the work is the honest
            // replacement.
            SanchrCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                ) {
                    Text(
                        text = "Verify a contact",
                        style = IosType.bodyBold,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text =
                            "Security codes are shared between two people. Open a contact's profile and " +
                                "tap Verify security code to compare or scan theirs.",
                        style = IosType.captionSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Signed Pre-Key Info
            Text(
                text = "Signed Pre-Key",
                style = IosType.bodyBold,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                ) {
                    // Read from the store, not asserted. This card used to
                    // print "Active" in green unconditionally beside a literal
                    // "Key ID: SPK-0001" and two "--" lines: fabricated
                    // security state, on the one screen whose whole purpose is
                    // to report the real thing.
                    val signedPreKey = keyState.signedPreKey
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = if (keyState.isActive) SanchrSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
                        Text(
                            text =
                                when {
                                    !keyState.loaded -> "Checking…"
                                    keyState.isActive -> "Active"
                                    else -> "Not generated yet"
                                },
                            style = IosType.sectionLabel,
                            color = if (keyState.isActive) SanchrSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    if (signedPreKey != null) {
                        Text(
                            text = "Key ID: " + signedPreKey.id,
                            style = IosType.captionSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Generated: " + formatKeyDate(signedPreKey.createdAtMillis),
                            style = IosType.captionSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (keyState.loaded) {
                        Text(
                            text = "This device has not published a signed pre-key yet.",
                            style = IosType.captionSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // OTP Key Count
            Text(
                text = "One-Time Pre-Keys",
                style = IosType.bodyBold,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(SanchrTheme.spacing.default),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
                        Text(
                            text = "Available Keys",
                            style = IosType.bodyBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text =
                            when {
                                !keyState.loaded -> "Counting…"
                                keyState.remainingPreKeys < 0 -> "The count could not be read."
                                else -> keyState.remainingPreKeys.toString() + " unused keys on this device"
                            },
                        style = IosType.captionSmall,
                        color =
                            if (keyState.loaded && keyState.remainingPreKeys in 0 until LOW_PREKEY_THRESHOLD) {
                                SanchrWarning
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "Keys are automatically replenished when the count drops below threshold.",
                        style = IosType.captionSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Key Change Notifications
            Text(
                text =
                    "You'll be notified when a contact's security key changes, " +
                        "which could mean they reinstalled the app or changed devices.",
                style = IosType.captionSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}

/** Below this many unused one-time pre-keys the count is worth drawing attention to. */
private const val LOW_PREKEY_THRESHOLD = 10

/** A key's creation date, in the device's own format. */
private fun formatKeyDate(epochMillis: Long): String =
    java.text.DateFormat
        .getDateInstance(java.text.DateFormat.MEDIUM)
        .format(java.util.Date(epochMillis))
