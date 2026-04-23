package com.sanchr.sync.realtime

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.EnvelopeKind
import com.sanchr.domain.messaging.IncomingEnvelopeContext
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.domain.messaging.ServerProvidedSender
import com.sanchr.proto.messaging.ClientEvent
import com.sanchr.proto.messaging.EncryptedEnvelope
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.ServerEvent
import com.sanchr.proto.messaging.TypingIndicator
import com.sanchr.sync.rotation.PreKeyReplenishWorker
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val appContext: Context,
        private val messagingClient: MessagingServiceClient,
        private val sessionManager: SessionManager,
        private val messageDao: MessageDao,
        private val receiveMessageUseCase: ReceiveMessageUseCase,
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
                is ServerEvent.PreKeyCountLow -> PreKeyReplenishWorker.enqueueOneTime(appContext)
                is ServerEvent.CallOffer -> {
                    Log.d(TAG, "Received call offer event ${event.offer?.callId.orEmpty()}")
                }
                is ServerEvent.CallLifecycle -> {
                    Log.d(TAG, "Received call lifecycle event ${event.event?.eventType.orEmpty()}")
                }
            }
        }

        private suspend fun persistIncomingEnvelope(envelope: EncryptedEnvelope) {
            val senderDeviceId = envelope.senderDevice.takeIf { it > 0 } ?: 1
            val result =
                receiveMessageUseCase.receive(
                    envelopeBytes = envelope.cipherText,
                    kind = EnvelopeKind.NON_SEALED,
                    serverTimestamp = envelope.serverTimestamp,
                    declaredSender = ServerProvidedSender(envelope.senderId, senderDeviceId),
                    envelopeContext =
                        IncomingEnvelopeContext(
                            conversationId = envelope.conversationId,
                            messageId = envelope.messageId,
                            contentType = envelope.contentType,
                        ),
                )
            if (result !is EnvelopeDecryptResult.Success) {
                Log.d(TAG, "Realtime envelope handled with result=$result")
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
