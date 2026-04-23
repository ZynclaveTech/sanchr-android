package com.sanchr.core.common

import java.util.concurrent.Executors
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Single-threaded dispatcher for libsignal operations.
 *
 * libsignal-client stores (identity, session, prekey) are not safe for
 * concurrent access across sessions. Route every libsignal call through
 * this dispatcher to serialize them.
 */
object SignalDispatcher {
    fun create(): CloseableCoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { r -> Thread(r, "signal-dispatcher") }
            .asCoroutineDispatcher()
}
