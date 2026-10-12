package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Gets a contact off a session this device can no longer read.
 *
 * A message that opens a session with a pre-key already spent, and that no
 * session held here reads, is someone still writing on a session this device
 * has lost. The envelope is sealed, so it does not say who. Each contact a
 * session is held for is therefore sent a control message on a new session:
 * receiving it moves that contact's device onto the new one, and what they
 * send next can be read. iOS does the same when it meets the equivalent.
 *
 * Without this the two devices stayed apart for good. The sender had no way
 * to learn its messages were unreadable, and went on sending them.
 *
 * Anyone can send such a message, so a contact is re-keyed at most once per
 * [SessionRebuildThrottle] cooldown. Re-keying costs a contact nothing: the
 * old session is set aside, not deleted, and stays readable.
 */
@Singleton
class StaleSessionHealer
    @Inject
    constructor(
        private val sessions: SignalSessionManager,
        // Lazy: sending depends on the same repository the receive path does.
        private val sendMessage: Lazy<SendMessageUseCase>,
        private val profileKeys: ProfileKeyStore,
        private val sessionManager: SessionManager,
        private val throttle: SessionRebuildThrottle,
        dispatchers: DispatcherProvider,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

        /** Called by the receive path; returns at once and heals on its own time. */
        fun unreadableMessageArrived() {
            scope.launch { heal() }
        }

        internal suspend fun heal() {
            val self = sessionManager.getUserId()
            val peers = sessions.sessionPeers().filter { it.name != self }
            for (peer in peers) {
                // Its own entry in the throttle. A rebuild of the same
                // contact's session for another reason must not use up the
                // one re-key that would have ended this.
                val claim = REKEY_CLAIM + peer.name
                if (!throttle.tryStart(claim, peer.deviceId)) {
                    Log.d(TAG, "re-key with ${peer.name.take(ID_PREFIX)}:${peer.deviceId} already made recently")
                    continue
                }
                try {
                    Log.i(TAG, "re-keying with ${peer.name.take(ID_PREFIX)}:${peer.deviceId} after an unreadable message")
                    sessions.resetSession(peer.name, peer.deviceId)
                    sendOnNewSession(peer.name)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "re-key with ${peer.name.take(ID_PREFIX)}:${peer.deviceId} failed", e)
                } finally {
                    throttle.finished(claim, peer.deviceId)
                }
            }
        }

        /**
         * The Profile Key, as iOS sends when it heals: something the contact
         * already has, that changes nothing on arrival, and is not subject to
         * the presence or receipt settings a heal must not depend on.
         */
        private suspend fun sendOnNewSession(peerId: String) {
            val key = profileKeys.ownProfileKey()
            val plaintext =
                InnerPayload(
                    conversationId = "",
                    messageId = null,
                    contentType = CONTENT_TYPE_PROFILE_KEY,
                    content = key,
                    senderProfileKey = key,
                ).encode()
            val encrypt =
                suspend {
                    sendMessage.get().encryptAndWrapDeviceMessages(
                        plaintext = plaintext,
                        recipients = listOf(peerId),
                        conversationId = "",
                        kind = SealedSendKind.Control,
                    )
                }
            val deviceMessages = encrypt()
            if (deviceMessages.isEmpty()) return
            sendMessage.get().sendControl(deviceMessages, encrypt)
        }

        private companion object {
            const val TAG = "StaleSessionHealer"
            const val CONTENT_TYPE_PROFILE_KEY = "profile-key/v1"
            const val ID_PREFIX = 8
            const val REKEY_CLAIM = "rekey/"
        }
    }
