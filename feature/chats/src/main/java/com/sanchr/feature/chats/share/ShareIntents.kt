package com.sanchr.feature.chats.share

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Reads a system share into [SharedContent], or null when the intent is not
 * a share or carried nothing sendable.
 *
 * Only the extraction lives here; what the pieces mean is
 * [SharedContentParser]'s job, which keeps that decision testable without
 * an Android runtime.
 */
fun Intent.toSharedContent(): SharedContent? {
    val uris =
        when (action) {
            Intent.ACTION_SEND -> listOfNotNull(streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> streamListExtra()
            else -> return null
        }
    return SharedContentParser.parse(
        uris = uris.map(Uri::toString),
        text = getStringExtra(Intent.EXTRA_TEXT),
        subject = getStringExtra(Intent.EXTRA_SUBJECT),
    )
}

@Suppress("DEPRECATION")
private fun Intent.streamExtra(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

@Suppress("DEPRECATION")
private fun Intent.streamListExtra(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
