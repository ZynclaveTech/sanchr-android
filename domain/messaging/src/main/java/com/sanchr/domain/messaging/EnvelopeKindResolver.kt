package com.sanchr.domain.messaging

import com.sanchr.proto.messaging.EncryptedEnvelope

/**
 * Maps a wire [EncryptedEnvelope] to the domain [EnvelopeKind] that drives
 * the decrypt path in [ReceiveMessageUseCase].
 *
 * Backend signals sealed-sender via content_type == "sealed"
 * (backend/crates/sanchr-core/src/messaging/service.rs:37). The
 * envelope_kind enum that previously lived in backend-oss/ was an
 * Android-side layering atop the deprecated fork — collapsed in Phase 1
 * of the backend canonicalization.
 *
 * Shared between [MessageDrainWorker][com.sanchr.sync.MessageDrainWorker],
 * [SyncWorker][com.sanchr.sync.SyncWorker], and
 * [RealtimeManager][com.sanchr.sync.realtime.RealtimeManager] so every
 * receive path makes the same routing decision.
 */
object EnvelopeKindResolver {
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    fun resolve(envelope: EncryptedEnvelope): EnvelopeKind =
        if (envelope.contentType == "sealed" ||
            (envelope.senderId == NIL_UUID && envelope.senderDevice == 0)
        ) {
            EnvelopeKind.SEALED
        } else {
            EnvelopeKind.NON_SEALED
        }
}
