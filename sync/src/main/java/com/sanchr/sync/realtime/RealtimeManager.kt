package com.sanchr.sync.realtime

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sanchr.core.common.calls.CallLifecycleSignal
import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.core.common.calls.IncomingCallOffer
import com.sanchr.core.common.calls.StreamWaker
import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.EnvelopeDecryptResult
import com.sanchr.domain.messaging.EnvelopeKind
import com.sanchr.domain.messaging.EnvelopeKindResolver
import com.sanchr.domain.messaging.IncomingEnvelopeContext
import com.sanchr.domain.messaging.PresenceStatus
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.domain.messaging.SendPresenceUseCase
import com.sanchr.domain.messaging.ServerProvidedSender
import com.sanchr.proto.messaging.CallLifecycleEvent
import com.sanchr.proto.messaging.CallOfferEvent
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
        private val incomingCallEvents: IncomingCallEvents,
        private val sendPresence: SendPresenceUseCase,
        // `@ApplicationScope` runs on Dispatchers.Default (see AppModule) and is shared with
        // other app-wide singletons, so everything reached from handleServerEvent() must stay
        // non-blocking. Blocking work belongs behind withContext(dispatchers.io), the way
        // ReceiveMessageUseCase.receive does it.
        @ApplicationScope private val appScope: CoroutineScope,
    ) : DefaultLifecycleObserver,
        StreamWaker {
        companion object {
            private const val TAG = "RealtimeManager"
            private const val BACKGROUND_DRAIN_DELAY_MS = 200L

            /** How long a call wake keeps the stream open in the background: the server's ring timeout. */
            const val CALL_WAKE_WINDOW_MS = 60_000L
            const val PRESENCE_INTERVAL_MS = 30_000L
        }

        private var inForeground = false
        private var wakeStopJob: Job? = null

        /** Peers whose chat is open, ref-counted; they get our presence while we are in the foreground. */
        private val trackedPeers = mutableMapOf<String, Int>()
        private var presenceLoop: Job? = null

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
                inForeground = true
                pendingStopJob?.cancel()
                pendingStopJob = null
                wakeStopJob?.cancel()
                wakeStopJob = null
                ensureStreamStartedLocked()
            }
            broadcastPresence(PresenceStatus.ONLINE)
            startPresenceLoop()
        }

        /** A chat with [userId] is on screen: announce ourselves and keep them in the presence loop. */
        fun trackPresencePeer(userId: String) {
            if (userId.isEmpty()) return
            val first =
                synchronized(lock) {
                    val previous = trackedPeers[userId] ?: 0
                    trackedPeers[userId] = previous + 1
                    previous == 0
                }
            if (first && inForeground) {
                appScope.launch { sendPresence(userId, PresenceStatus.ONLINE) }
                startPresenceLoop()
            }
        }

        fun untrackPresencePeer(userId: String) {
            synchronized(lock) {
                val count = trackedPeers[userId] ?: return
                if (count > 1) trackedPeers[userId] = count - 1 else trackedPeers.remove(userId)
            }
        }

        private fun broadcastPresence(status: PresenceStatus) {
            val peers = synchronized(lock) { trackedPeers.keys.toList() }
            peers.forEach { peer -> appScope.launch { sendPresence(peer, status) } }
        }

        /**
         * iOS re-announces ONLINE every 30 s; peers stop showing "Online" two
         * missed beats later. Runs only while there is someone to tell, so an
         * idle app holds no timer.
         */
        private fun startPresenceLoop() {
            if (presenceLoop?.isActive == true) return
            presenceLoop =
                appScope.launch {
                    while (inForeground && synchronized(lock) { trackedPeers.isNotEmpty() }) {
                        delay(PRESENCE_INTERVAL_MS)
                        if (inForeground) broadcastPresence(PresenceStatus.ONLINE)
                    }
                }
        }

        /**
         * A call push arrived while backgrounded. The offer itself is only
         * delivered on the message stream (queued server-side until we
         * reconnect), so open the stream now and keep it up for the ring
         * window, then close it again unless the app came to the foreground
         * in the meantime.
         */
        override fun wakeForCall(callId: String) {
            if (sessionManager.getAccessToken().isNullOrEmpty()) return
            synchronized(lock) {
                pendingStopJob?.cancel()
                pendingStopJob = null
                ensureStreamStartedLocked()
                wakeStopJob?.cancel()
                wakeStopJob =
                    appScope.launch {
                        delay(CALL_WAKE_WINDOW_MS)
                        synchronized(lock) {
                            if (wakeStopJob === coroutineContext[Job] && !inForeground) {
                                Log.d(TAG, "call wake window for $callId over; closing stream")
                                stopStreamLocked()
                            }
                            if (wakeStopJob === coroutineContext[Job]) wakeStopJob = null
                        }
                    }
            }
        }

        fun enterBackground() {
            synchronized(lock) { inForeground = false }
            presenceLoop?.cancel()
            presenceLoop = null
            if (!sessionManager.getAccessToken().isNullOrEmpty()) broadcastPresence(PresenceStatus.OFFLINE)
            if (sessionManager.getAccessToken().isNullOrEmpty()) {
                stopStream()
                return
            }

            synchronized(lock) {
                // A live call wake keeps the stream up for its window.
                if (wakeStopJob?.isActive == true) return
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
                is ServerEvent.CallOffer -> event.offer?.let { deliverCallOffer(it) }
                is ServerEvent.CallLifecycle -> event.event?.let { deliverCallLifecycle(it) }
            }
        }

        /**
         * Hands a call offer to the call engine. Its failure must not take
         * the message stream down with it: a bad offer is one call, the
         * stream is every message.
         */
        private suspend fun deliverCallOffer(offer: CallOfferEvent) {
            Log.d(TAG, "Call offer ${offer.callId} from device ${offer.callerDevice}")
            try {
                incomingCallEvents.onCallOffer(
                    IncomingCallOffer(
                        callId = offer.callId,
                        callerId = offer.callerId,
                        callType = offer.callType,
                        encryptedSdpPayload = offer.encryptedSdpPayload,
                        callerDevice = offer.callerDevice,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Call offer ${offer.callId} could not be handled", e)
            }
        }

        private suspend fun deliverCallLifecycle(event: CallLifecycleEvent) {
            Log.d(TAG, "Call lifecycle ${event.eventType} for ${event.callId}")
            try {
                incomingCallEvents.onCallLifecycle(CallLifecycleSignal(event.callId, event.peerId, event.eventType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Call lifecycle for ${event.callId} could not be handled", e)
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
