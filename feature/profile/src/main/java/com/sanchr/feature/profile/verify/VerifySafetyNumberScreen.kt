package com.sanchr.feature.profile.verify

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.crypto.verify.SafetyNumber
import com.sanchr.core.crypto.verify.ScanOutcome
import com.sanchr.core.designsystem.component.QrCodes
import com.sanchr.core.designsystem.component.QrScanner
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.component.SanchrCard
import com.sanchr.core.designsystem.component.SanchrTopBar
import com.sanchr.core.designsystem.theme.SanchrTheme
import java.text.DateFormat
import java.util.Date

/**
 * One contact's safety number: the digits to read aloud, the QR to scan, and
 * the verification the user can record or revoke.
 *
 * Mirrors iOS `VerifySecurityCodeView`. Both screens compute the same number
 * from the same two identity keys, so a cross-platform pair can compare.
 */
@Composable
fun VerifySafetyNumberScreen(
    contactName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerifySafetyNumberViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.scannerOpen) {
        ScannerOverlay(onScanned = viewModel::onScanned, onClose = viewModel::closeScanner)
        return
    }

    Scaffold(
        // The NavHost's Scaffold has already inset this for the system
        // bars; applying them again counts the status bar twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SanchrTopBar(title = "Verify security code", onNavigateBack = onNavigateBack) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(SanchrTheme.spacing.default),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            uiState.scanOutcome?.let { outcome ->
                ScanOutcomeBanner(outcome = outcome, onDismiss = viewModel::dismissScanOutcome)
            }

            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> UnavailableNotice(message = uiState.error.orEmpty())
                else ->
                    uiState.safetyNumber?.let { number ->
                        SafetyNumberBody(
                            number = number,
                            contactName = contactName,
                            onScan = viewModel::openScanner,
                            onMarkVerified = viewModel::markVerifiedManually,
                            onClearVerification = viewModel::clearVerification,
                        )
                    }
            }
        }
    }
}

@Composable
private fun SafetyNumberBody(
    number: SafetyNumber,
    contactName: String,
    onScan: () -> Unit,
    onMarkVerified: () -> Unit,
    onClearVerification: () -> Unit,
) {
    QrCard(payload = number.scannablePayload)

    Text(
        text = "Scan this code on $contactName's phone, or compare the digits below out loud.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = SanchrTheme.spacing.md),
    )

    DigitGrid(groups = number.digitGroups)

    VerificationStatus(verifiedAtMillis = number.verifiedAtMillis)

    SanchrButton(
        text = "Scan their code",
        onClick = onScan,
        modifier = Modifier.fillMaxWidth().padding(top = SanchrTheme.spacing.md),
    )

    if (number.verifiedAtMillis == null) {
        TextButton(onClick = onMarkVerified) { Text("Mark as verified") }
    } else {
        TextButton(onClick = onClearVerification) { Text("Clear verification") }
    }
}

@Composable
private fun QrCard(payload: ByteArray) {
    val bitmap =
        remember(payload) {
            QrCodes.binaryBitmap(payload, QR_SIZE_PX)?.asImageBitmap()
        }
    SanchrCard {
        Box(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                Text(
                    text = "This code isn't ready yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Safety number QR code",
                    modifier = Modifier.size(QR_SIZE_DP.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** The 60 digits, in the twelve groups of five both apps print. */
@Composable
private fun DigitGrid(groups: List<String>) {
    SanchrCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
        ) {
            groups.chunked(GROUPS_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { group ->
                        Text(
                            text = group,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationStatus(verifiedAtMillis: Long?) {
    val label =
        when (verifiedAtMillis) {
            null -> "Not verified"
            else -> "Verified on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(verifiedAtMillis))
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
        modifier = Modifier.padding(top = SanchrTheme.spacing.md),
    ) {
        Icon(
            imageVector = if (verifiedAtMillis == null) Icons.Filled.Info else Icons.Filled.Verified,
            contentDescription = null,
            tint = if (verifiedAtMillis == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Why no code is on screen. Deliberately has no retry-with-placeholder path:
 * a fabricated number would read the same on both phones and let someone
 * verify a session that was never checked.
 */
@Composable
private fun UnavailableNotice(message: String) {
    SanchrCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScanOutcomeBanner(
    outcome: ScanOutcome,
    onDismiss: () -> Unit,
) {
    val message =
        when (outcome) {
            ScanOutcome.Match -> "Security codes match. This conversation is verified."
            ScanOutcome.Mismatch -> "Those codes do not match. Someone may be intercepting this conversation."
            ScanOutcome.Unreadable -> "That code could not be read, so nothing was verified. Try again."
        }
    SanchrCard(modifier = Modifier.padding(bottom = SanchrTheme.spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
        }
    }
}

/** Full-screen camera, with the payload handed straight to the comparison. */
@Composable
private fun ScannerOverlay(
    onScanned: (ByteArray) -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        QrScanner(
            onScanned = { payload -> onScanned(payload.bytes) },
            modifier = Modifier.fillMaxSize(),
            onPermissionDenied = onClose,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White) }
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White)
            Text(
                text = "Point at their security code",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(start = SanchrTheme.spacing.sm),
            )
        }
    }
}

private const val QR_SIZE_PX = 512
private const val QR_SIZE_DP = 200
private const val GROUPS_PER_ROW = 4
