package com.sanchr.core.callengine

import android.util.Log
import com.sanchr.proto.calling.CallSignal
import com.sanchr.proto.calling.CallSignalPayload
import com.sanchr.proto.calling.CallSignalingServiceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The bidirectional `CallStream` for one call, as iOS drives it: a fresh
 * outbound channel whose first frame is the `CallJoin` the server
 * requires, local ICE candidates buffered until the stream is open, a
 * `ping` every 20 s, and up to five reconnects with backoff before the
 * call is given up.
 */
internal class CallStream(
    private val client: CallSignalingServiceClient,
    private val scope: CoroutineScope,
    private val localDeviceId: () -> Int,
    private val isMuted: () -> Boolean,
    /** Whether [callId] is still the call in progress; a stream for another call stops itself. */
    private val isCurrent: (callId: String) -> Boolean,
    private val onSignal: suspend (callId: String, signal: CallSignal) -> Unit,
    private val onFailed: suspend (callId: String) -> Unit,
) {
    private var outbound: Channel<CallSignal>? = null
    private val pendingLocalIce = mutableListOf<ByteArray>()
    private var job: Job? = null
    private var keepAlive: Job? = null

    fun open(
        callId: String,
        role: String,
    ) {
        job?.cancel()
        outbound?.close()
        val channel = newOutbound(callId, role)
        job =
            scope.launch {
                var attempt = 0
                var requests = channel
                while (true) {
                    val startedAt = System.currentTimeMillis()
                    try {
                        client.callStream(requests.receiveAsFlow()).collect { onSignal(callId, it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Signaling stream error for $callId: ${e.message}")
                    }
                    if (!isCurrent(callId)) return@launch
                    if (System.currentTimeMillis() - startedAt > HEALTHY_AFTER_MS) attempt = 0
                    attempt++
                    if (attempt > MAX_RECONNECTS) {
                        Log.e(TAG, "Signaling stream failed $attempt times; giving up on $callId")
                        onFailed(callId)
                        return@launch
                    }
                    Log.w(TAG, "Signaling stream ended mid-call; reconnecting ($attempt/$MAX_RECONNECTS)")
                    delay(minOf(attempt, RECONNECT_MAX_STEPS) * RECONNECT_STEP_MS)
                    if (!isCurrent(callId)) return@launch
                    outbound?.close()
                    requests = newOutbound(callId, role)
                }
            }
        startKeepAlive(callId)
    }

    fun send(
        callId: String,
        payload: CallSignalPayload,
    ) {
        val channel = outbound
        if (channel == null || channel.trySend(CallSignal(callId, localDeviceId(), payload)).isFailure) {
            if (payload is CallSignalPayload.IceCandidate) pendingLocalIce += payload.json
        }
    }

    /** A local ICE candidate gathered before there is a call id to send it under. */
    fun bufferIce(json: ByteArray) {
        pendingLocalIce += json
    }

    fun close() {
        keepAlive?.cancel()
        keepAlive = null
        job?.cancel()
        job = null
        outbound?.close()
        outbound = null
        pendingLocalIce.clear()
    }

    private fun newOutbound(
        callId: String,
        role: String,
    ): Channel<CallSignal> {
        val channel = Channel<CallSignal>(Channel.UNLIMITED)
        outbound = channel
        val answererDevice = if (role == ROLE_CALLEE) localDeviceId() else 0
        channel.trySend(CallSignal(callId, localDeviceId(), CallSignalPayload.Join(role, answererDevice)))
        pendingLocalIce.forEach { channel.trySend(CallSignal(callId, localDeviceId(), CallSignalPayload.IceCandidate(it))) }
        pendingLocalIce.clear()
        channel.trySend(CallSignal(callId, localDeviceId(), CallSignalPayload.Control(if (isMuted()) "muted" else "unmuted")))
        return channel
    }

    private fun startKeepAlive(callId: String) {
        keepAlive?.cancel()
        keepAlive =
            scope.launch {
                while (isCurrent(callId)) {
                    delay(KEEPALIVE_MS)
                    if (isCurrent(callId)) send(callId, CallSignalPayload.Control("ping"))
                }
            }
    }

    companion object {
        const val ROLE_CALLER = "caller"
        const val ROLE_CALLEE = "callee"
        private const val TAG = "CallStream"
        private const val KEEPALIVE_MS = 20_000L
        private const val MAX_RECONNECTS = 5
        private const val RECONNECT_STEP_MS = 500L
        private const val RECONNECT_MAX_STEPS = 4
        private const val HEALTHY_AFTER_MS = 30_000L
    }
}
