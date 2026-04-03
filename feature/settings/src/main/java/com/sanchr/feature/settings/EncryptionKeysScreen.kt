package com.sanchr.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrSuccess
import com.sanchr.core.designsystem.theme.SanchrTheme
import com.sanchr.core.designsystem.theme.SanchrWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncryptionKeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            SanchrTopBar(
                title = "Encryption Keys",
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(SanchrTheme.spacing.default),
        ) {
            // Identity Fingerprint section
            Text(
                text = "Your Identity Fingerprint",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier = Modifier
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                    // 12-block hex grid (4 rows x 3 columns)
                    val fingerprintBlocks = listOf(
                        "05ae72", "b31c0f", "d894a2",
                        "71fe6b", "39c5d8", "a20e47",
                        "f8b163", "6d2c95", "1e74ba",
                        "c309f6", "87d41a", "5b0e2c",
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
                    ) {
                        fingerprintBlocks.forEachIndexed { index, block ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = SanchrTheme.spacing.xs)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = block.uppercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
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

            // QR Code section
            Text(
                text = "Safety Number QR Code",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SanchrTheme.spacing.default),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // QR code placeholder
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Safety number QR code",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                    Text(
                        text = "Scan this code with a contact to verify your safety number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

                    SanchrButton(
                        text = "Scan QR Code",
                        onClick = { /* open scanner */ },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = SanchrTheme.spacing.xl))

            // Signed Pre-Key Info
            Text(
                text = "Signed Pre-Key",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SanchrTheme.spacing.default),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = SanchrSuccess,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(SanchrTheme.spacing.sm))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelMedium,
                            color = SanchrSuccess,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "Key ID: SPK-0001",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Generated: --",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Last rotated: --",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // OTP Key Count
            Text(
                text = "One-Time Pre-Keys",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.md))

            SanchrCard {
                Column(
                    modifier = Modifier
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
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "-- keys remaining on server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(SanchrTheme.spacing.xs))
                    Text(
                        text = "Keys are automatically replenished when the count drops below threshold.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xl))

            // Key Change Notifications
            Text(
                text = "You'll be notified when a contact's security key changes, which could mean they reinstalled the app or changed devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(SanchrTheme.spacing.xxl))
        }
    }
}
