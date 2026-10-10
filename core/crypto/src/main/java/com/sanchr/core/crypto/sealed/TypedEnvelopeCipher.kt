package com.sanchr.core.crypto.sealed

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.store.SanchrIdentityKeyStore
import com.sanchr.core.crypto.store.SanchrSessionStore
import com.sanchr.core.crypto.store.SanchrSignalProtocolStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore

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
 * as changed, and attribute the stranger's message to them. So a first message
 * is never opened under a real address. It is opened under a throwaway one,
 * and only once the payload has said who it is from, *and that claim has been
 * checked*, is the session moved to where it belongs:
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
                .filterNot { it.name.startsWith(SCRATCH_PREFIX) }
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

        /**
         * One at a time. Each run clears leftover throwaway sessions before
         * it starts, which would otherwise take out one that another run had
         * opened and not yet placed.
         */
        private val firstMessages = Mutex()

        private suspend fun openFirstMessage(
            message: PreKeySignalMessage,
            declaredSender: (ByteArray) -> DeclaredSender?,
            publishedIdentity: suspend (String, Int) -> IdentityKey?,
        ): SealedSenderCipher.DecryptedEnvelope =
            firstMessages.withLock { openFirstMessageLocked(message, declaredSender, publishedIdentity) }

        private suspend fun openFirstMessageLocked(
            message: PreKeySignalMessage,
            declaredSender: (ByteArray) -> DeclaredSender?,
            publishedIdentity: suspend (String, Int) -> IdentityKey?,
        ): SealedSenderCipher.DecryptedEnvelope {
            val scratch = SignalProtocolAddress(SCRATCH_PREFIX + UUID.randomUUID(), 1)
            val identity = message.identityKey
            try {
                val plaintext =
                    withContext(dispatchers.signalDispatcher) {
                        // Anything a previous run left behind when it was
                        // killed between opening a message and placing it.
                        forgetScratch()
                        SessionCipher(store, scratch).decrypt(message)
                    }
                val sender =
                    declaredSender(plaintext)?.takeIf { it.userId.isNotBlank() && !it.userId.startsWith(SCRATCH_PREFIX) }
                        ?: throw InvalidMessageException("first message of a session does not say who it is from")
                if (!isVouchedFor(sender, identity, publishedIdentity)) {
                    throw UntrustedIdentityException("${sender.userId} is not the holder of the key this message was sent with")
                }
                withContext(dispatchers.signalDispatcher) { adopt(scratch, sender, identity) }
                return SealedSenderCipher.DecryptedEnvelope(sender.userId, sender.deviceId, plaintext)
            } finally {
                // Even when cancelled: the throwaway session is key material.
                withContext(NonCancellable + dispatchers.signalDispatcher) { forgetScratch() }
            }
        }

        private suspend fun isVouchedFor(
            sender: DeclaredSender,
            identity: IdentityKey,
            publishedIdentity: suspend (String, Int) -> IdentityKey?,
        ): Boolean {
            val known = withContext(dispatchers.signalDispatcher) { identityStore.isKnownIdentityOf(sender.userId, identity) }
            if (known) return true
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

        /** Moves the session from the throwaway address to the sender's own, under the usual trust rules. */
        private fun adopt(
            scratch: SignalProtocolAddress,
            sender: DeclaredSender,
            identity: IdentityKey,
        ) {
            val address = SignalProtocolAddress(sender.userId, sender.deviceId)
            // Records a changed key for the user to review, exactly as an
            // ordinary receive would.
            if (!store.isTrustedIdentity(address, identity, IdentityKeyStore.Direction.RECEIVING)) {
                throw UntrustedIdentityException("identity for ${sender.userId} is not trusted for receiving")
            }
            // `loadSession` hands back an empty record for an address with no
            // session, which would silently replace the sender's real one.
            check(store.containsSession(scratch)) { "the opened session is gone before it could be placed" }
            store.saveIdentity(address, identity)
            store.storeSession(address, store.loadSession(scratch))
        }

        private fun forgetScratch() {
            sessionStore.forgetNamesStartingWith(SCRATCH_PREFIX)
            identityStore.forgetNamesStartingWith(SCRATCH_PREFIX)
        }

        private companion object {
            const val TAG = "TypedEnvelopeCipher"

            /** Not a user id any server issues, so it can never collide with a contact. */
            const val SCRATCH_PREFIX = "sealedscratch:"
            const val VOUCH_ATTEMPTS = 3
            const val VOUCH_RETRY_DELAY_MS = 400L
        }
    }
