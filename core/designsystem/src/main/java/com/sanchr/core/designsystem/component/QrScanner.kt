package com.sanchr.core.designsystem.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live camera QR scanner.
 *
 * Reports the first code it reads and then stops decoding, so a caller that
 * navigates away on the result cannot be handed a second frame mid-transition.
 * Decoding happens on a single background executor; the camera is unbound and
 * the executor shut down when the composable leaves.
 *
 * [onPermissionDenied] fires when the user refuses the camera, letting the
 * caller show its own explanation rather than leaving a black rectangle.
 */
@Composable
fun QrScanner(
    onScanned: (QrPayload) -> Unit,
    modifier: Modifier = Modifier,
    onPermissionDenied: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnDenied by rememberUpdatedState(onPermissionDenied)
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            granted = allowed
            if (!allowed) currentOnDenied()
        }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Box(modifier = modifier)
        return
    }

    // One executor and one "already reported" latch for the life of the
    // scanner, so recomposition never spawns a second decode thread.
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reported = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).also { view ->
                bindScanner(viewContext, view, lifecycleOwner, executor) { result ->
                    if (reported.compareAndSet(false, true)) currentOnScanned(QrCodes.payloadOf(result))
                }
            }
        },
    )
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun bindScanner(
    context: Context,
    view: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    executor: ExecutorService,
    onResult: (Result) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val analysis =
            ImageAnalysis
                .Builder()
                // Only the newest frame matters: a backlog would decode stale
                // images long after the user moved the phone.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
        analysis.setAnalyzer(executor) { proxy ->
            proxy.use { frame -> frame.decodeQr(reader)?.let(onResult) }
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Decodes one camera frame, or null when it holds no QR code.
 *
 * Reads the Y plane only. It is the luminance channel the binarizer wants, and
 * skipping the chroma planes keeps the whole decode allocation-light enough to
 * run on every frame.
 */
private fun ImageProxy.decodeQr(reader: MultiFormatReader): Result? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source =
        PlanarYUVLuminanceSource(
            bytes,
            plane.rowStride,
            height,
            0,
            0,
            width.coerceAtMost(plane.rowStride),
            height,
            false,
        )
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
    } catch (_: Exception) {
        // NotFoundException on most frames — the common case, not an error.
        null
    } finally {
        reader.reset()
    }
}
