package com.sanchr.core.crypto.sealed

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.SenderCertificateRequest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.signal.libsignal.metadata.certificate.SenderCertificate

/**
 * Holds the current [SenderCertificate] issued by the server's sealed-sender
 * authority and refreshes it before expiry (per spec §4, refresh ~24h before
 * `expiration`).
 *
 * The cache is seeded from [SessionManager] on first use so a cold start
 * doesn't force a backend round-trip. Refreshes go over
 * `MessagingService.GetSenderCertificate` and are mirrored back to
 * [SessionManager.saveSenderCertificate].
 *
 * Thread-safety: [cached] is volatile; [current] / [refresh] are suspend and
 * serialize refresh work on [DispatcherProvider.io].
 */
@Singleton
class SenderCertificateManager
    @Inject
    constructor(
        private val dispatchers: DispatcherProvider,
        private val messagingClient: MessagingServiceClient,
        private val sessionManager: SessionManager,
    ) {
        @Volatile private var cached: SenderCertificate? = null

        @Volatile private var hydratedFromDisk: Boolean = false

        /**
         * Returns the current certificate, refreshing if missing or within the
         * 24h grace window.
         */
        suspend fun current(): SenderCertificate =
            withContext(dispatchers.io) {
                hydrateFromDiskIfNeeded()
                val c = cached
                if (c != null && !c.isExpiringSoon()) c else refresh()
            }

        /**
         * Fetches a fresh certificate from the server and persists the bytes
         * to [SessionManager] so the cache survives process restarts.
         */
        suspend fun refresh(): SenderCertificate =
            withContext(dispatchers.io) {
                val response =
                    messagingClient.getSenderCertificate(SenderCertificateRequest())
                val bytes = response.certificate
                check(bytes.isNotEmpty()) {
                    "MessagingService.GetSenderCertificate returned an empty certificate"
                }
                val cert = SenderCertificate(bytes)
                cached = cert
                runCatching { sessionManager.saveSenderCertificate(bytes) }
                    .onFailure { Log.w(TAG, "Failed to persist sender certificate", it) }
                cert
            }

        /**
         * Test / bootstrap helper: sets the cached certificate, bypassing the
         * refresh RPC. Intended for unit tests only.
         */
        fun setForTest(certificate: SenderCertificate) {
            cached = certificate
            hydratedFromDisk = true
        }

        /** Clears the cache (e.g. on logout / account wipe). */
        fun clear() {
            cached = null
            hydratedFromDisk = true // disk is about to be wiped by caller
            runCatching { sessionManager.clearSenderCertificate() }
        }

        private fun hydrateFromDiskIfNeeded() {
            if (hydratedFromDisk) return
            hydratedFromDisk = true
            val bytes = sessionManager.getSenderCertificate() ?: return
            runCatching { SenderCertificate(bytes) }
                .onSuccess { cached = it }
                .onFailure {
                    Log.w(TAG, "Discarding corrupt persisted sender certificate", it)
                    runCatching { sessionManager.clearSenderCertificate() }
                }
        }

        private fun SenderCertificate.isExpiringSoon(): Boolean = expiration < System.currentTimeMillis() + REFRESH_GRACE_MS

        private companion object {
            const val TAG = "SenderCertificateManager"
            val REFRESH_GRACE_MS: Long = TimeUnit.HOURS.toMillis(24)
        }
    }
