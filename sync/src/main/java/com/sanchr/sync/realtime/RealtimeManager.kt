package com.sanchr.sync.realtime

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.EnvelopeKind
import com.sanchr.domain.messaging.EnvelopeKindResolver
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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
        // `@ApplicationScope` runs on Dispatchers.Default (see AppModule) and is shared with
        // other app-wide singletons, so everything reached from handleServerEvent() must stay
        // non-blocking. Blocking work belongs behind withContext(dispatchers.io), the way
        // ReceiveMessageUseCase.receive does it.
        @ApplicationScope private val appScope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        companion object {
            private const val TAG = "RealtimeManager"
            private const val BACKGROUND_DRAIN_DELAY_MS = 200L
        }

        private val outboundEvents = Channel<ClientEvent>(capacity = Channel.BUFFERED)
        private val _typingCache = MutableStateFlow<Map<String, TypingIndicator>>(emptyMap())

        val typingCache: StateFlow<Map<String, TypingIndicator>> = _typingCache.asStateFlow()

        private val lock = Any()
        private var streamJob: Job? = null
        private var pendingStopJob: Job? = null
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
            synchronized(lock) {
                pendingStopJob?.cancel()
                pendingStopJob = null
                ensureStreamStartedLocked()
            }
        }

        fun enterBackground() {
            if (sessionManager.getAccessToken().isNullOrEmpty()) {
                stopStream()
                return
            }

            synchronized(lock) {
                pendingStopJob?.cancel()
                val job =
                    appScope.launch(start = CoroutineStart.LAZY) {
                        delay(BACKGROUND_DRAIN_DELAY_MS)
                        synchronized(lock) {
                            if (pendingStopJob === coroutineContext[Job]) {
                                stopStreamLocked()
                                pendingStopJob = null
                            }
                        }
                    }
                pendingStopJob = job
                job.start()
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

        // Must be called with `lock` held: cancelling any pending stop and (re)starting the
        // stream have to happen as one atomic step, otherwise a stop that is already past its
        // delay and blocked on the lock can see the not-yet-cleared `pendingStopJob` and tear
        // down the stream this call just started.
        private fun ensureStreamStartedLocked() {
            if (streamJob != null) return

            val job =
                appScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        messagingClient.messageStream(outboundEvents.receiveAsFlow()).collect { event ->
                            handleServerEvent(event)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Exception) {
                        Log.e(TAG, "Realtime stream failed", error)
                    } finally {
                        synchronized(lock) {
                            if (streamJob === coroutineContext[Job]) {
                                streamJob = null
                            }
                        }
                    }
                }
            streamJob = job
            job.start()
        }

        private fun stopStream() {
            synchronized(lock) { stopStreamLocked() }
        }

        private fun stopStreamLocked() {
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
            val domainKind = EnvelopeKindResolver.resolve(envelope)
            // Sealed envelopes carry the sender inside the encrypted blob;
            // passing the (nil-sentinel) server-declared sender through would
            // confuse the sealed decrypt path. Mirrors MessageDrainWorker.
            val declaredSender =
                if (domainKind == EnvelopeKind.SEALED) {
                    null
                } else {
                    ServerProvidedSender(envelope.senderId, senderDeviceId)
                }
            val result =
                receiveMessageUseCase.receive(
                    envelopeBytes = envelope.cipherText,
                    kind = domainKind,
                    serverTimestamp = envelope.serverTimestamp,
                    declaredSender = declaredSender,
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
