package com.sanchr.core.callengine

import android.content.Context
import android.media.AudioManager
import com.sanchr.core.callengine.signaling.CallSignalingCrypto
import com.sanchr.core.callengine.signaling.SealedCallPayload
import com.sanchr.core.common.calls.CallLifecycleSignal
import com.sanchr.core.common.calls.CallPeerNames
import com.sanchr.core.common.calls.IncomingCallOffer
import com.sanchr.core.crypto.SignalSessionManager
import com.sanchr.core.datastore.SessionManager
import com.sanchr.proto.calling.CallSignal
import com.sanchr.proto.calling.CallSignalPayload
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.EndCallResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.SessionDescription

/**
 * The iOS voice→video handshake: `video_request` → `video_accept` → sealed
 * renegotiation offer (type "offer") → sealed answer; `video_decline` and
 * `video_failed` back out.
 */
class CallManagerVideoUpgradeTest {
    private val webRTC =
        mockk<WebRTCClient>(relaxed = true) {
            every { events } returns MutableSharedFlow()
            every { setVideoEnabled(any()) } answers { firstArg() }
            coEvery { createOffer() } returns SessionDescription(SessionDescription.Type.OFFER, "v=0 upgrade-offer")
            coEvery { createAnswer() } returns SessionDescription(SessionDescription.Type.ANSWER, "v=0 answer")
        }

    /** Everything the manager puts on the signaling stream, and a way to feed it frames. */
    private val sent = mutableListOf<CallSignal>()
    private val inbound = MutableSharedFlow<CallSignal>(extraBufferCapacity = 8)
    private val client =
        mockk<CallSignalingServiceClient>(relaxed = true) {
            coEvery { endCall(any()) } returns EndCallResponse
            every { callStream(any()) } answers {
                val requests = firstArg<Flow<CallSignal>>()
                merge(inbound, flow { requests.collect { sent += it } })
            }
        }
    private val crypto =
        mockk<CallSignalingCrypto> {
            coEvery { sealAnswer(any(), any(), any(), any()) } answers { "sealed:${secondArg<String>()}".toByteArray() }
        }
    private val signal = mockk<SignalSessionManager>(relaxed = true)
    private val session = mockk<SessionManager> { every { getDeviceId() } returns "1" }
    private val context =
        mockk<Context>(relaxed = true) {
            every { getSystemService(Context.AUDIO_SERVICE) } returns mockk<AudioManager>(relaxed = true)
        }

    private fun TestScope.manager() =
        CallManager(
            webRTC,
            client,
            crypto,
            signal,
            session,
            CallPeerNames {
                "Alice"
            },
            CallPlatform(context),
            backgroundScope,
        )

    private fun controls() = sent.mapNotNull { (it.payload as? CallSignalPayload.Control)?.action }

    /** Answers an incoming voice call from Alice (device 2) so the manager is Active on a voice call. */
    private suspend fun TestScope.activeVoiceCall(): CallManager {
        val manager = manager()
        coEvery { crypto.open(byteArrayOf(9), "alice", 2, 30.0) } returns SealedCallPayload("v=0 offer", null, "sha-256 AA", 1.0)
        manager.onCallOffer(IncomingCallOffer("call-1", "alice", "voice", byteArrayOf(9), callerDevice = 2))
        manager.answerCall()
        runCurrent()
        assertTrue(manager.callState.value is CallState.Active)
        assertEquals("voice", manager.callType.value)
        sent.clear()
        return manager
    }

    private suspend fun CallManager.control(action: String) = onCallLifecycle(CallLifecycleSignal("call-1", "alice", action))

    @Test
    fun `requesting video in a voice call sends video_request once, and video_accept triggers a sealed renegotiation offer`() =
        runTest {
            val manager = activeVoiceCall()

            manager.toggleVideo()
            manager.toggleVideo()
            runCurrent()
            assertEquals(listOf("video_request"), controls())
            assertTrue(manager.outgoingVideoUpgradePending.value)

            manager.control("video_accept")
            runCurrent()
            assertEquals("video", manager.callType.value)
            assertTrue(manager.peerVideoEnabled.value)
            coVerify { webRTC.setVideoEnabled(true) }
            coVerify { crypto.sealAnswer("v=0 upgrade-offer", "offer", "alice", 2) }
            val sealed = sent.filterIsInstance<CallSignal>().mapNotNull { it.payload as? CallSignalPayload.EncryptedSdpAnswer }.single()
            assertEquals("sealed:offer", String(sealed.ciphertext))

            // The peer's answer to the renegotiation clears the pending flag.
            coEvery { crypto.open(any(), "alice", 2, 30.0) } returns SealedCallPayload("v=0 peer-answer", "answer", "sha-256 BB", 1.0)
            inbound.emit(CallSignal("call-1", 2, CallSignalPayload.EncryptedSdpAnswer(byteArrayOf(7))))
            runCurrent()
            assertFalse(manager.outgoingVideoUpgradePending.value)
            coVerify {
                webRTC.setRemoteDescription(
                    match { it.type == SessionDescription.Type.ANSWER && it.description == "v=0 peer-answer" },
                )
            }
        }

    @Test
    fun `video_decline clears the pending request and leaves the call on voice`() =
        runTest {
            val manager = activeVoiceCall()
            manager.requestVideoUpgrade()
            manager.control("video_decline")
            runCurrent()
            assertFalse(manager.outgoingVideoUpgradePending.value)
            assertEquals("voice", manager.callType.value)
        }

    @Test
    fun `a peer's video_request surfaces a prompt, and accepting turns on the camera and replies video_accept and video_on`() =
        runTest {
            val manager = activeVoiceCall()
            manager.control("video_request")
            assertTrue(manager.incomingVideoUpgradeRequest.value)

            manager.acceptVideoUpgrade()
            runCurrent()
            assertFalse(manager.incomingVideoUpgradeRequest.value)
            assertEquals("video", manager.callType.value)
            assertEquals(listOf("video_accept", "video_on"), controls())

            // The requester's sealed offer is answered with a sealed answer.
            coEvery { crypto.open(any(), "alice", 2, 30.0) } returns SealedCallPayload("v=0 peer-offer", "offer", "sha-256 CC", 1.0)
            inbound.emit(CallSignal("call-1", 2, CallSignalPayload.EncryptedSdpAnswer(byteArrayOf(8))))
            runCurrent()
            coVerify {
                webRTC.setRemoteDescription(
                    match { it.type == SessionDescription.Type.OFFER && it.description == "v=0 peer-offer" },
                )
            }
            coVerify { crypto.sealAnswer("v=0 answer", "answer", "alice", 2) }
            assertTrue(manager.peerVideoEnabled.value)
        }

    @Test
    fun `declining a peer's request replies video_decline, and video_failed drops an upgraded call back to voice`() =
        runTest {
            val manager = activeVoiceCall()
            manager.control("video_request")
            manager.declineVideoUpgrade()
            runCurrent()
            assertEquals(listOf("video_decline"), controls())
            assertFalse(manager.incomingVideoUpgradeRequest.value)

            manager.control("video_request")
            manager.acceptVideoUpgrade()
            assertEquals("video", manager.callType.value)
            manager.control("video_failed")
            assertEquals("voice", manager.callType.value)
            assertFalse(manager.peerVideoEnabled.value)
            coVerify { webRTC.setVideoEnabled(false) }
        }
}
