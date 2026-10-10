package com.sanchr.core.crypto.sealed

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrSessionStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * The envelope the iOS app sends: one type byte, then a plain Signal message,
 * with nothing around it and no padding inside it.
 *
 *     0x01 || PreKeySignalMessage      first message of a session
 *     0x02 || SignalMessage            every message after
 *
 * This app sends libsignal's sealed-sender envelope instead, and until this
 * existed could read nothing else, so the two apps could not decrypt each
 * other's messages at all. The first byte tells the formats apart: a
 * sealed-sender envelope starts with its version (0x11, 0x22) and a bare
 * Signal message with its own (0x33 and up), never with 0x01 or 0x02.
 */
object TypedEnvelope {
    const val PREKEY: Byte = 0x01
    const val SIGNAL: Byte = 0x02

    fun isTyped(envelope: ByteArray): Boolean = envelope.size > 1 && (envelope[0] == PREKEY || envelope[0] == SIGNAL)
}

/** The sender a decrypted payload says it is from. */
data class DeclaredSender(
    val userId: String,
    val deviceId: Int,
)

/**
 * Decrypts [TypedEnvelope]s.
 *
 * When the envelope arrives with a sender the server named, that is the
 * session it is opened with. On the sealed path there is no sender anywhere
 * outside the ciphertext, and the work here is in finding the right session
 * without damaging a wrong one.
 *
 * iOS does that by trying every session it holds. For a message inside an
 * established session that is sound — only the right ratchet authenticates it,
 * and a failed attempt changes nothing — so it is what happens here too.
 *
 * For the first message of a session it is not. A PreKeySignalMessage carries
 * everything needed to start a session and so succeeds against *any* address
 * it is tried under, and on the receiving side a changed identity key is
 * accepted rather than refused. Tried against the wrong contact, it would
 * replace that contact's session with a stranger's, flag their safety number
 * as changed, and attribute the stranger's message to them.
 *
 * So a first message is read twice. The first reading is a rehearsal: it runs
 * against [RehearsalStore], which keeps what it writes in memory and consumes
 * nothing, and its only purpose is to learn who the payload says the message
 * is from. That claim is then checked:
 *
 *  - the identity key inside the message has to be one already on record for
 *    the user the payload names, or
 *  - the key the server publishes for that user, asked for now.
 *
 * Without that check the payload's word would be the only thing tying a key
 * to a person, and anyone could introduce themselves as anyone this device
 * had not yet spoken to. A sealed-sender envelope carries a server-signed
 * certificate for exactly this; this format carries nothing, so the question
 * is put to the server directly.
 *
 * Only then is the message opened for real, under the sender's own address
 * and with the real store, exactly as an envelope that named its sender would
 * be. That second reading is libsignal's ordinary one, and it keeps two
 * things true that copying a session into place did not: a replayed first
 * message is recognised against the session it already started, instead of
 * rolling that session back to its beginning; and a contact's earlier
 * ratchets are archived beside the new one rather than thrown away, so
 * messages still in flight on them can be read.
 */
@Singleton
class TypedEnvelopeCipher
    @Inject
    constructor(
        private val store: SanchrSignalProtocolStore,
        private val sessionStore: SanchrSessionStore,
        private val identityStore: SanchrIdentityKeyStore,
        private val dispatchers: DispatcherProvider,
    ) {
        /** Opens an envelope whose sender the server named. */
        suspend fun decryptFrom(
            envelope: ByteArray,
            senderId: String,
            senderDevice: Int,
        ): ByteArray =
            withContext(dispatchers.signalDispatcher) {
                val cipher = SessionCipher(store, SignalProtocolAddress(senderId, senderDevice))
                val body = envelope.copyOfRange(1, envelope.size)
                when (envelope[0]) {
                    TypedEnvelope.PREKEY -> cipher.decrypt(PreKeySignalMessage(body))
                    else -> cipher.decrypt(SignalMessage(body))
                }
            }

        /**
         * Opens an envelope with no sender outside the ciphertext.
         *
         * @param declaredSender reads the sender out of a decrypted payload.
         * @param publishedIdentity the identity key the server publishes for a
         *   user's device, or null if it publishes none. May throw on a
         *   transport failure.
         */
        suspend fun decryptSealed(
            envelope: ByteArray,
            declaredSender: (ByteArray) -> DeclaredSender?,
            publishedIdentity: suspend (userId: String, deviceId: Int) -> IdentityKey?,
        ): SealedSenderCipher.DecryptedEnvelope {
            val body = envelope.copyOfRange(1, envelope.size)
            return when (envelope[0]) {
                TypedEnvelope.PREKEY -> openFirstMessage(PreKeySignalMessage(body), declaredSender, publishedIdentity)
                else -> withContext(dispatchers.signalDispatcher) { openWithinASession(SignalMessage(body)) }
            }
        }

        /** Tries each session; only the right one authenticates the message, and a miss leaves a session as it was. */
        private fun openWithinASession(message: SignalMessage): SealedSenderCipher.DecryptedEnvelope =
            sessionStore
                .allAddresses()
                .firstNotNullOfOrNull { address ->
                    tryOpen(message, address)?.let { SealedSenderCipher.DecryptedEnvelope(address.name, address.deviceId, it) }
                }
                // Worded without "session": the receive path reads that word in
                // this exception as "rebuild and wait", and would leave an
                // envelope nothing can ever open on the server to be redelivered
                // forever.
                ?: throw InvalidMessageException("no ratchet held by this device authenticates the envelope")

        /** The plaintext if [address]'s session authenticates [message], else null. */
        private fun tryOpen(
            message: SignalMessage,
            address: SignalProtocolAddress,
        ): ByteArray? =
            try {
                SessionCipher(store, address).decrypt(message)
            } catch (e: DuplicateMessageException) {
                // Only the session it belongs to can recognise a replay.
                throw e
            } catch (e: Exception) {
                null
            }

        private suspend fun openFirstMessage(
            message: PreKeySignalMessage,
            declaredSender: (ByteArray) -> DeclaredSender?,
            publishedIdentity: suspend (String, Int) -> IdentityKey?,
        ): SealedSenderCipher.DecryptedEnvelope {
            val identity = message.identityKey
            // The rehearsal: nothing it does outlives this call.
            val claimed =
                withContext(dispatchers.signalDispatcher) {
                    SessionCipher(RehearsalStore(store), REHEARSAL_ADDRESS).decrypt(message)
                }
            val sender =
                declaredSender(claimed)?.takeIf { it.userId.isNotBlank() }
                    ?: throw InvalidMessageException("first message of a session does not say who it is from")
            if (!isVouchedFor(sender, identity, publishedIdentity)) {
                throw UntrustedIdentityException("${sender.userId} is not the holder of the key this message was sent with")
            }
            // For real, under the sender's own address. A replay fails here
            // as a duplicate, and a changed key is recorded for the user to
            // review, by the same code as any other receive.
            val plaintext =
                withContext(dispatchers.signalDispatcher) {
                    SessionCipher(store, SignalProtocolAddress(sender.userId, sender.deviceId)).decrypt(message)
                }
            return SealedSenderCipher.DecryptedEnvelope(sender.userId, sender.deviceId, plaintext)
        }

        private suspend fun isVouchedFor(
            sender: DeclaredSender,
            identity: IdentityKey,
            publishedIdentity: suspend (String, Int) -> IdentityKey?,
        ): Boolean {
            val known = withContext(dispatchers.signalDispatcher) { identityStore.isKnownIdentityOf(sender.userId, identity) }
            if (known) return true
            if (!spendLookup()) return false
            // A stranger, or a contact whose key has changed. Asked more than
            // once because the message cannot be opened a second time: giving
            // up on a dropped connection would lose it for good.
            repeat(VOUCH_ATTEMPTS) { attempt ->
                try {
                    return publishedIdentity(sender.userId, sender.deviceId) == identity
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "could not ask for the published identity (attempt ${attempt + 1}): ${e.message}")
                    delay(VOUCH_RETRY_DELAY_MS * (attempt + 1))
                }
            }
            return false
        }

        /**
         * Takes one from the window's budget; false when it is spent.
         *
         * Each question to the server is triggered by an envelope from
         * someone not yet identified, so without a ceiling the sender chooses
         * how many are asked.
         */
        private fun spendLookup(): Boolean =
            synchronized(lookupLock) {
                if (lookupWindow.elapsedNow() >= LOOKUP_WINDOW) {
                    lookupWindow = TimeSource.Monotonic.markNow()
                    lookupsInWindow = 0
                }
                (lookupsInWindow < LOOKUP_BUDGET).also { if (it) lookupsInWindow++ }
            }

        private val lookupLock = Any()
        private var lookupWindow = TimeSource.Monotonic.markNow()
        private var lookupsInWindow = 0

        private companion object {
            const val TAG = "TypedEnvelopeCipher"

            /** Exists only in [RehearsalStore]'s memory. */
            val REHEARSAL_ADDRESS = SignalProtocolAddress("rehearsal", 1)
            const val VOUCH_ATTEMPTS = 3
            const val VOUCH_RETRY_DELAY_MS = 400L
            const val LOOKUP_BUDGET = 60
            val LOOKUP_WINDOW = 5.minutes
        }
    }

/**
 * The store a first message is rehearsed against: the real keys to read, and
 * nowhere real to write.
 *
 * Sessions and identities it is asked to save are kept in memory and dropped
 * with it. Pre-keys are read from the real store and never removed or marked
 * used, so the message can be opened again for real afterwards.
 */
private class RehearsalStore(
    private val real: SignalProtocolStore,
) : SignalProtocolStore by real {
    private val sessions = HashMap<SignalProtocolAddress, SessionRecord>()
    private val identities = HashMap<SignalProtocolAddress, IdentityKey>()

    // Sessions: in memory only.
    override fun loadSession(address: SignalProtocolAddress): SessionRecord = sessions[address] ?: SessionRecord()

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> = addresses.mapNotNull { sessions[it] }

    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()

    override fun storeSession(
        address: SignalProtocolAddress,
        record: SessionRecord,
    ) {
        sessions[address] = record
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = address in sessions

    override fun deleteSession(address: SignalProtocolAddress) {
        sessions.remove(address)
    }

    override fun deleteAllSessions(name: String) {
        sessions.keys.removeAll { it.name == name }
    }

    // Identity: what it learns of others is not kept, and nothing is being
    // trusted — this reading only finds out who to ask about.
    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        identities[address] = identityKey
        return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = identities[address]

    // Pre-keys: read, never consumed.
    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord,
    ) = Unit

    override fun removePreKey(preKeyId: Int) = Unit

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) = Unit

    override fun removeSignedPreKey(signedPreKeyId: Int) = Unit

    override fun storeKyberPreKey(
        kyberPreKeyId: Int,
        record: KyberPreKeyRecord,
    ) = Unit

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ECPublicKey,
    ) = Unit
}
