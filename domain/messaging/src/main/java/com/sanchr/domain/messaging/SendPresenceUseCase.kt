package com.sanchr.domain.messaging

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.crypto.profile.ProfileKeyStore
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import sanchr.messaging.Messaging

/**
 * Tells one peer whether we are online, as iOS `sendP2PPresence`: a
 * `PresenceUpdate` inside a sealed `presence/v1` control payload, so the
 * server never learns who is watching whom. With online status turned
 * off in settings the peer is told HIDDEN instead, matching iOS's privacy
 * gate; the peer then shows nothing.
 */
class SendPresenceUseCase
    @Inject
    constructor(
        private val sendMessageUseCase: SendMessageUseCase,
        private val sessionManager: SessionManager,
        private val userPreferences: UserPreferences,
        private val profileKeyStore: ProfileKeyStore,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        suspend operator fun invoke(
            peerId: String,
            status: PresenceStatus,
        ) {
            withContext(dispatcherProvider.io) {
                try {
                    send(peerId, status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "presence send to $peerId failed", e)
                }
            }
        }

        private suspend fun send(
            peerId: String,
            requested: PresenceStatus,
        ) {
            if (peerId.isEmpty()) return
            val selfUserId = sessionManager.getUserId()
            if (selfUserId.isNullOrBlank()) return
            // Sanchr Mode says nothing at all, not even "hidden".
            if (!userPreferences.canSendPresence.first()) return
            val status = if (userPreferences.onlineStatusVisible.first()) requested else PresenceStatus.HIDDEN
            val now = System.currentTimeMillis()
            val update =
                Messaging.PresenceUpdate
                    .newBuilder()
                    .setUserId(selfUserId)
                    .setStatusCode(status.toWire())
                    .setStatus(status.name.lowercase())
                    .setLastSeen(if (status == PresenceStatus.ONLINE) 0L else now)
                    .build()
            val payload =
                InnerPayload(
                    conversationId = "",
                    messageId = null,
                    contentType = CONTENT_TYPE_PRESENCE,
                    content = update.toByteArray(),
                    senderProfileKey = profileKeyStore.ownProfileKey(),
                )
            val plaintext = payload.encode()
            val encrypt =
                suspend {
                    sendMessageUseCase.encryptAndWrapDeviceMessages(
                        plaintext = plaintext,
                        recipients = listOf(peerId),
                        conversationId = "",
                        kind = SealedSendKind.Control,
                    )
                }
            val deviceMessages = encrypt()
            if (deviceMessages.isEmpty()) return
            sendMessageUseCase.sendControl(deviceMessages, encrypt)
        }

        private fun PresenceStatus.toWire(): Messaging.PresenceStatus =
            when (this) {
                PresenceStatus.ONLINE -> Messaging.PresenceStatus.ONLINE
                PresenceStatus.OFFLINE -> Messaging.PresenceStatus.OFFLINE
                PresenceStatus.HIDDEN -> Messaging.PresenceStatus.HIDDEN
            }

        companion object {
            private const val TAG = "SendPresenceUseCase"
            const val CONTENT_TYPE_PRESENCE = "presence/v1"
        }
    }
