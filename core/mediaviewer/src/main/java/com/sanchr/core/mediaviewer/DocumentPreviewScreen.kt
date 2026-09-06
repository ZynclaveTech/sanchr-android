package com.sanchr.core.mediaviewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sanchr.core.designsystem.component.SanchrButton
import com.sanchr.core.designsystem.theme.SanchrTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of a text attachment is read before it is truncated. */
private const val TEXT_PREVIEW_LIMIT = 512 * 1024

/** A4-ish, used to hold a page's place until it renders. */
private const val PAGE_PLACEHOLDER_ASPECT = 0.707f

/**
 * Shows an attachment without handing it to another app.
 *
 * Android previously opened every document with ACTION_VIEW, which grants a
 * third-party app read access to the decrypted file. For an app whose premise
 * is that a conversation goes nowhere the user did not send it, a bank
 * statement opening in whichever PDF reader is installed is the wrong
 * default. iOS has never done this — QuickLook previews in process.
 *
 * Anything the platform genuinely cannot render still offers another app, but
 * as a decision the user makes rather than the only thing that happens.
 */
@Composable
fun DocumentPreviewScreen(
    file: File,
    fileName: String?,
    mimeType: String?,
    onDismiss: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SanchrTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
                Text(
                    text = fileName ?: "Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenExternally) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open with another app",
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (DocumentPreviewSupport.kindOf(mimeType, fileName)) {
                    DocumentPreviewKind.PDF -> PdfBody(file)
                    DocumentPreviewKind.IMAGE -> ImageBody(file, onOpenExternally)
                    DocumentPreviewKind.TEXT -> TextBody(file)
                    DocumentPreviewKind.EXTERNAL -> UnsupportedBody(onOpenExternally)
                }
            }
        }
    }
}

@Composable
private fun PdfBody(file: File) {
    var pdf by remember(file) { mutableStateOf<PdfPages?>(null) }
    var opened by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        val pages = PdfPages.open(file)
        pdf = pages
        opened = true
        onDispose {
            pages?.close()
            pdf = null
        }
    }

    // BoxWithConstraints so a page is rasterised at the width it is shown at,
    // rather than at a guessed resolution that is either soft or wasteful.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = SanchrTheme.spacing.sm),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val pages = pdf

        when {
            !opened ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            pages == null || pages.pageCount == 0 ->
                CentredMessage("This PDF could not be opened here. It may be password-protected or damaged.")

            else ->
                // Rendered page by page as they scroll into view. Rendering
                // the whole document up front meant a file from a stranger
                // decided how much memory to allocate: a few hundred pages is
                // an ordinary report, and holding all of them as bitmaps is
                // enough to take the process down.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(pages.pageCount) { index ->
                        PdfPage(pages = pages, index = index, widthPx = widthPx)
                    }
                }
        }
    }
}

/**
 * One page, rendered when it comes into view and dropped when it leaves.
 *
 * The placeholder keeps its height so the list does not jump as pages
 * resolve; a page that cannot be rendered — outside the size budget, or
 * damaged — simply stays a placeholder rather than failing the document.
 */
@Composable
private fun PdfPage(
    pages: PdfPages,
    index: Int,
    widthPx: Int,
) {
    var page by remember(pages, index, widthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pages, index, widthPx) {
        if (widthPx > 0) {
            page = pages.render(index, widthPx)
        }
    }

    when (val rendered = page) {
        null ->
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(PAGE_PLACEHOLDER_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

        else ->
            ComposeImage(
                bitmap = rendered.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Composable
private fun ImageBody(
    file: File,
    onOpenExternally: () -> Unit,
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val decoded = withContext(Dispatchers.IO) { BoundedBitmaps.decode(file) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    when {
        // A declared image that will not decode — a mislabelled or truncated
        // file — is the one case where the type says previewable and is wrong.
        failed -> UnsupportedBody(onOpenExternally)
        bitmap == null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        else ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                ComposeImage(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
    }
}

@Composable
private fun TextBody(file: File) {
    var text by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        text =
            withContext(Dispatchers.IO) {
                runCatching { readCapped(file) }.getOrElse { "" }
            }
    }

    when (val body = text) {
        null ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        else ->
            Text(
                text = body.ifEmpty { "This file is empty." },
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(SanchrTheme.spacing.default),
            )
    }
}

/**
 * At most [TEXT_PREVIEW_LIMIT] bytes of [file], decoded as UTF-8.
 *
 * Capped because a multi-megabyte log would otherwise be held in memory twice
 * and laid out as a single Text node.
 *
 * Read in a loop rather than with `InputStream.readNBytes`, which is API 33
 * and only available below that through core library desugaring. Relying on
 * a desugared method means the file this lives in cannot move to a module
 * that has not enabled it — which is exactly how this surfaced.
 */
private fun readCapped(file: File): String {
    val buffer = ByteArray(TEXT_PREVIEW_LIMIT)
    var filled = 0
    file.inputStream().use { stream ->
        while (filled < buffer.size) {
            val read = stream.read(buffer, filled, buffer.size - filled)
            if (read <= 0) break
            filled += read
        }
    }
    return String(buffer, 0, filled, Charsets.UTF_8)
}

@Composable
private fun UnsupportedBody(onOpenExternally: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SanchrTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SanchrTheme.spacing.default, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sanchr can't display this file type.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            // Said plainly, because it is a real consequence rather than a
            // formality: the file is decrypted, and opening it elsewhere
            // gives that app access to it.
            text = "Opening it in another app gives that app access to the decrypted file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        SanchrButton(text = "Open with another app", onClick = onOpenExternally)
    }
}

@Composable
private fun CentredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(SanchrTheme.spacing.lg), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
