package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.database.dao.QuarantinedEnvelopeDao
import com.sanchr.core.database.entity.QuarantinedEnvelopeEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Persists an undecryptable envelope into `quarantined_envelopes` for
 * operator / future-UI review. Called by [ReceiveMessageUseCase] after
 * classifying a libsignal failure as non-transient. Deliberately returns
 * Unit — the caller already has an `EnvelopeDecryptResult.Quarantined` in
 * hand and does not need the row id.
 *
 * The WARN log is load-bearing for M3 — there is no UI surface yet, so a
 * logcat trail is the only way to spot crypto regressions during dev /
 * interop testing.
 */
@Singleton
class QuarantineEnvelopeUseCase
    @Inject
    constructor(
        private val quarantinedEnvelopeDao: QuarantinedEnvelopeDao,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend fun quarantine(
            envelopeId: String,
            payload: ByteArray,
            receivedAt: Long,
            senderUserId: String?,
            senderDeviceId: Int?,
            failureClass: FailureClass,
            failureMessage: String?,
        ) = withContext(dispatchers.io) {
            quarantinedEnvelopeDao.insert(
                QuarantinedEnvelopeEntity(
                    envelopeId = envelopeId,
                    receivedAt = receivedAt,
                    payload = payload,
                    senderUserId = senderUserId,
                    senderDeviceId = senderDeviceId,
                    failureClass = failureClass.name,
                    failureMessage = failureMessage,
                    attempts = 0,
                ),
            )
            Log.w(TAG, "envelope $envelopeId → $failureClass: $failureMessage")
        }

        private companion object {
            private const val TAG = "Quarantine"
        }
    }
