package com.sanchr.core.crypto.sealed

import com.sanchr.core.common.DispatcherProvider
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
 * In M2 the refresh RPC (`MessagingService.GetSenderCertificate`) exists in
 * the generated gRPC stubs but is not yet wired end-to-end on Android (no
 * `KeyServiceClient` / `MessagingServiceClient` injection surface here yet).
 * Tests inject a pre-minted certificate via [setForTest]; production callers
 * will hit [refresh] which currently throws to make the gap loud.
 *
 * Thread-safety: [cached] is volatile; [current] / [refresh] are suspend and
 * serialize refresh work on [DispatcherProvider.io].
 */
@Singleton
class SenderCertificateManager
    @Inject
    constructor(
        private val dispatchers: DispatcherProvider,
    ) {
        @Volatile private var cached: SenderCertificate? = null

        /**
         * Returns the current certificate, refreshing if missing or within the
         * 24h grace window.
         */
        suspend fun current(): SenderCertificate =
            withContext(dispatchers.io) {
                val c = cached
                if (c != null && !c.isExpiringSoon()) c else refresh()
            }

        /**
         * Fetches a fresh certificate from the server. Not yet wired — see the
         * KDoc on this class. Will be implemented in M3 by injecting the
         * messaging-service gRPC client and calling `getSenderCertificate()`.
         */
        suspend fun refresh(): SenderCertificate =
            withContext(dispatchers.io) {
                throw UnsupportedOperationException(
                    "SenderCertificate refresh RPC is not yet wired — M3 work (see MessagingService.GetSenderCertificate)",
                )
            }

        /**
         * Test / bootstrap helper: sets the cached certificate, bypassing the
         * refresh RPC. The only supported path in M2 for populating the cache.
         */
        fun setForTest(certificate: SenderCertificate) {
            cached = certificate
        }

        /** Clears the cache (e.g. on logout / account wipe). */
        fun clear() {
            cached = null
        }

        private fun SenderCertificate.isExpiringSoon(): Boolean = expiration < System.currentTimeMillis() + REFRESH_GRACE_MS

        private companion object {
            val REFRESH_GRACE_MS: Long = TimeUnit.HOURS.toMillis(24)
        }
    }
