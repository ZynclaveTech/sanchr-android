package com.sanchr.sync.realtime

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sanchr.core.crypto.SignalKeyManager
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.proto.messaging.ClientEvent
import com.sanchr.proto.messaging.EncryptedEnvelope
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.ServerEvent
import com.sanchr.proto.messaging.TypingIndicator
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class RealtimeManager
    @Inject
    constructor(
        private val messagingClient: MessagingServiceClient,
        private val sessionManager: SessionManager,
        private val signalSessionManager: SignalSessionManager,
        private val signalKeyManager: SignalKeyManager,
        private val messageRepository: Lazy<MessageRepository>,
        private val messageDao: MessageDao,
    ) : DefaultLifecycleObserver {
        companion object {
            private const val TAG = "RealtimeManager"
            private const val BACKGROUND_DRAIN_DELAY_MS = 200L
        }

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val outboundEvents = Channel<ClientEvent>(capacity = Channel.BUFFERED)
        private val _typingCache = MutableStateFlow<Map<String, TypingIndicator>>(emptyMap())

        val typingCache: StateFlow<Map<String, TypingIndicator>> = _typingCache.asStateFlow()

        private var streamJob: Job? = null
        private var initialized = false

        fun initialize() {
            if (initialized) return
            initialized = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        override fun onStart(owner: LifecycleOwner) {
            enterForeground()
        }

        override fun onStop(owner: LifecycleOwner) {
            enterBackground()
        }

        fun enterForeground() {
            if (sessionManager.getAccessToken().isNullOrEmpty()) return
            ensureStreamStarted()
        }

        fun enterBackground() {
            if (sessionManager.getAccessToken().isNullOrEmpty()) {
                stopStream()
                return
            }

            appScope.launch {
                delay(BACKGROUND_DRAIN_DELAY_MS)
                stopStream()
            }
        }

        fun sendTypingIndicator(
            conversationId: String,
            isTyping: Boolean,
        ) {
            val userId = sessionManager.getUserId().orEmpty()
            appScope.launch {
                outboundEvents.send(
                    ClientEvent.Typing(
                        conversationId = conversationId,
                        userId = userId,
                        isTyping = isTyping,
                    ),
                )
            }
        }

        private fun ensureStreamStarted() {
            if (streamJob != null) return

            streamJob =
                appScope.launch {
                    try {
                        messagingClient.messageStream(outboundEvents.receiveAsFlow()).collect { event ->
                            handleServerEvent(event)
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Realtime stream failed", error)
                    } finally {
                        streamJob = null
                    }
                }
        }

        private fun stopStream() {
            streamJob?.cancel()
            streamJob = null
        }

        private suspend fun handleServerEvent(event: ServerEvent) {
            when (event) {
                is ServerEvent.Message -> event.envelope?.let { persistIncomingEnvelope(it) }
                is ServerEvent.Typing -> handleTyping(event.indicator)
                is ServerEvent.Receipt -> handleReceipt(event.update)
                is ServerEvent.PreKeyCountLow -> signalKeyManager.checkAndReplenishPreKeys()
                is ServerEvent.CallOffer -> {
                    Log.d(TAG, "Received call offer event ${event.offer?.callId.orEmpty()}")
                }
                is ServerEvent.CallLifecycle -> {
                    Log.d(TAG, "Received call lifecycle event ${event.event?.eventType.orEmpty()}")
                }
            }
        }

        private suspend fun persistIncomingEnvelope(envelope: EncryptedEnvelope) {
            runCatching {
                val decrypted = signalSessionManager.decryptEnvelope(envelope)
                val plaintext = String(decrypted.plaintext, Charsets.UTF_8)
                messageRepository.get().insertDecryptedMessage(
                    conversationId = decrypted.conversationId,
                    messageId = decrypted.messageId,
                    senderId = decrypted.senderId,
                    content = plaintext,
                    contentType = decrypted.contentType,
                    timestamp = decrypted.serverTimestamp,
                )
            }.onFailure { error ->
                Log.w(TAG, "Failed to decrypt realtime envelope", error)
            }
        }

        private suspend fun handleReceipt(update: com.sanchr.proto.messaging.ReceiptUpdate?) {
            if (update == null || update.messageId.isBlank()) return
            runCatching {
                messageDao.updateMessageStatus(update.messageId, update.status.uppercase())
            }.onFailure { error ->
                Log.w(TAG, "Failed to apply receipt update", error)
            }
        }

        private fun handleTyping(indicator: TypingIndicator?) {
            if (indicator == null || indicator.conversationId.isBlank()) return
            _typingCache.update { current ->
                current.toMutableMap().apply {
                    if (indicator.isTyping) {
                        put(indicator.conversationId, indicator)
                    } else {
                        remove(indicator.conversationId)
                    }
                }
            }
        }
    }
