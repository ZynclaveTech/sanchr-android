package com.sanchr.feature.contacts.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanchr.core.designsystem.component.QrScanner
import com.sanchr.core.designsystem.theme.IosType
import com.sanchr.core.designsystem.theme.SanchrTheme

/** The camera, looking for someone's profile code to start a chat with them. */
@Composable
fun ScanContactScreen(
    onClose: () -> Unit,
    onOpenConversation: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanContactViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.opened.collect(onOpenConversation) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // The camera is only up while looking: not behind a problem, where
        // it would keep reading the same wrong code.
        if (state == ScanContactState.Scanning) {
            QrScanner(
                onScanned = { payload -> viewModel.onScanned(payload.text) },
                modifier = Modifier.fillMaxSize(),
                onPermissionDenied = viewModel::onCameraRefused,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color.White) }
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White)
            Text(
                text = "Point at their Sanchr code",
                style = IosType.body,
                color = Color.White,
                modifier = Modifier.padding(start = SanchrTheme.spacing.sm),
            )
        }
        when (val current = state) {
            ScanContactState.Scanning -> Unit
            ScanContactState.Opening -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            is ScanContactState.Problem ->
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(SanchrTheme.spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
                ) {
                    Text(text = current.message, style = IosType.body, color = Color.White, textAlign = TextAlign.Center)
                    TextButton(onClick = viewModel::scanAgain) { Text("Scan again", color = Color.White) }
                }
        }
    }
}
