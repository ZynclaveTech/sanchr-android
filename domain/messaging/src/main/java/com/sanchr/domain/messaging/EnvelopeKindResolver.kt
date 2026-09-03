package com.sanchr.domain.messaging

import com.sanchr.proto.messaging.EncryptedEnvelope
import com.sanchr.proto.messaging.EnvelopeKind as ProtoEnvelopeKind

/**
 * Maps a wire [EncryptedEnvelope] to the domain [EnvelopeKind] that drives
 * the decrypt path in [ReceiveMessageUseCase].
 *
 * Prefer the wire-level [ProtoEnvelopeKind] when the backend populates it.
 * When we see [ProtoEnvelopeKind.UNSPECIFIED] the server is either
 * pre-rollout or replaying an old row that predates the field — fall back
 * to the legacy sentinel (`content_type == "sealed"` and/or nil sender +
 * device 0) that the backend has used since sealed sender shipped.
 *
 * Shared between [MessageDrainWorker][com.sanchr.sync.MessageDrainWorker],
 * [SyncWorker][com.sanchr.sync.SyncWorker], and
 * [RealtimeManager][com.sanchr.sync.realtime.RealtimeManager] so every
 * receive path makes the same routing decision. Duplicating the logic
 * caused sealed realtime envelopes to be misrouted and quarantined
 * (see M3 review — the realtime path hardcoded NON_SEALED).
 */
object EnvelopeKindResolver {
    private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

    fun resolve(envelope: EncryptedEnvelope): EnvelopeKind =
        when (envelope.envelopeKind) {
            ProtoEnvelopeKind.SEALED -> EnvelopeKind.SEALED
            ProtoEnvelopeKind.NORMAL -> EnvelopeKind.NON_SEALED
            ProtoEnvelopeKind.UNSPECIFIED ->
                if (envelope.contentType == "sealed" ||
                    (envelope.senderId == NIL_UUID && envelope.senderDevice == 0)
                ) {
                    EnvelopeKind.SEALED
                } else {
                    EnvelopeKind.NON_SEALED
                }
        }
}
