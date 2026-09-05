package com.sanchr.core.callengine

import android.content.Context
import android.media.AudioManager
import com.sanchr.core.callengine.signaling.CallPayloadException
import com.sanchr.core.callengine.signaling.CallSignalingCrypto
import com.sanchr.core.callengine.signaling.SealedCallPayload
import com.sanchr.core.common.calls.CallPeerNames
import com.sanchr.core.common.calls.IncomingCallOffer
import com.sanchr.core.common.calls.IncomingCallPush
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.EndCallRequest
import com.sanchr.proto.calling.EndCallResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The push-first incoming path: ring from the push, take the SDP from the stream, or fail closed. */
class CallManagerIncomingTest {
    private val webRTC = mockk<WebRTCClient>(relaxed = true) { every { events } returns MutableSharedFlow() }
    private val client = mockk<CallSignalingServiceClient>(relaxed = true) { coEvery { endCall(any()) } returns EndCallResponse }
    private val crypto = mockk<CallSignalingCrypto>()
    private val signal = mockk<SignalSessionManager>(relaxed = true)
    private val session = mockk<SessionManager> { every { getDeviceId() } returns "1" }
    private val names = CallPeerNames { "Alice" }
    private val context =
        mockk<Context>(relaxed = true) {
            every { getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>(relaxed = true)
        }

    private fun TestScope.manager() = CallManager(webRTC, client, crypto, signal, session, names, CallPlatform(context), backgroundScope)

    private val push = IncomingCallPush("call-1", "alice", "video", callerDevice = 2)
    private val offer = IncomingCallOffer("call-1", "alice", "video", byteArrayOf(9), callerDevice = 2)

    @Test
    fun `a push rings immediately with the caller's name and no SDP, then the stream's offer fills the SDP`() =
        runTest {
            val manager = manager()
            manager.onCallPush(push)
            val ringing = manager.callState.value as CallState.Incoming
            assertEquals("call-1", ringing.callId)
            assertEquals("Alice", ringing.callerName)
            assertTrue(ringing.isVideo)
            assertEquals("", ringing.sdpOffer)
            assertEquals(2, ringing.callerDevice)

            coEvery { crypto.open(byteArrayOf(9), "alice", 2, 120.0) } returns SealedCallPayload("v=0 offer", null, "sha-256 AA", 1.0)
            manager.onCallOffer(offer)
            val ready = manager.callState.value as CallState.Incoming
            assertEquals("v=0 offer", ready.sdpOffer)
        }

    @Test
    fun `a second push for the same call, and an offer for a different call while ringing, are rejected sensibly`() =
        runTest {
            val manager = manager()
            manager.onCallPush(push)
            manager.onCallPush(push)
            assertTrue(manager.callState.value is CallState.Incoming)

            manager.onCallOffer(IncomingCallOffer("call-2", "bob", "voice", byteArrayOf(1), 1))
            coVerify { client.endCall(EndCallRequest("call-2", "busy")) }
            assertEquals("call-1", (manager.callState.value as CallState.Incoming).callId)
        }

    @Test
    fun `an offer that fails to decrypt ends the pushed call as failed and resets the dead session`() =
        runTest {
            val manager = manager()
            manager.onCallPush(push)
            coEvery { crypto.open(any(), any(), any(), any()) } throws CallPayloadException("stale")

            manager.onCallOffer(offer)

            coVerify { signal.resetSession("alice", 2) }
            coVerify { client.endCall(EndCallRequest("call-1", "failed")) }
            val ended = manager.callState.value as CallState.Ended
            assertEquals(CallState.EndReason.FAILED, ended.reason)
        }

    @Test
    fun `declining a pushed call before its SDP arrived tells the server and ends locally`() =
        runTest {
            val manager = manager()
            manager.onCallPush(push)
            manager.declineCall()
            runCurrent()
            coVerify { client.endCall(EndCallRequest("call-1", "declined")) }
            assertEquals(CallState.EndReason.DECLINED, (manager.callState.value as CallState.Ended).reason)
        }
}
