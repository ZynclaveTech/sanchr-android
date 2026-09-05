package com.sanchr.sync.realtime

import android.content.Context
import android.util.Log
import com.sanchr.core.common.calls.CallLifecycleSignal
import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.MessageReactionDao
import com.sanchr.core.database.entity.MessageReactionEntity
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.PresenceStatus
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.domain.messaging.SendPresenceUseCase
import com.sanchr.proto.messaging.CallLifecycleEvent
import com.sanchr.proto.messaging.CallOfferEvent
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.Reaction
import com.sanchr.proto.messaging.ServerEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Exercises the `enterForeground()` / `enterBackground()` state machine directly
 * (no ProcessLifecycleOwner / Robolectric involved).
 *
 * Every coroutine RealtimeManager launches runs on a [StandardTestDispatcher] tied to
 * `testScheduler`, so time only moves when we explicitly advance it. `Dispatchers.Unconfined`
 * is deliberately not used anywhere here: it would run launched coroutines inline and collapse
 * exactly the interleaving these tests exist to observe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeManagerTest {
    private val appContext = mockk<Context>(relaxed = true)
    private val messagingClient = mockk<MessagingServiceClient>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val reactionDao = mockk<MessageReactionDao>(relaxed = true)
    private val receiveMessageUseCase = mockk<ReceiveMessageUseCase>(relaxed = true)
    private val incomingCallEvents = mockk<IncomingCallEvents>(relaxed = true)
    private val sendPresence = mockk<SendPresenceUseCase>(relaxed = true)

    /** Counts how many `messageStream(...).collect { }` invocations are live right now. */
    private val activeCollectors = AtomicInteger(0)

    /** High-water mark of [activeCollectors], to catch overlap even if it's momentary. */
    private val maxConcurrentCollectors = AtomicInteger(0)

    /** How many times `messageStream(...)` was collected from at all. */
    private val totalStarts = AtomicInteger(0)

    /** Whether the most recently completed collection was cancelled rather than finishing normally. */
    private val lastCollectionWasCancelled = AtomicBoolean(false)

    /**
     * Stands in for the real gRPC stream: a cold flow that never emits and never completes on
     * its own. Every `collect` re-runs this block, so overlapping collectors show up as
     * `activeCollectors` exceeding 1 (defect B) instead of being silently absorbed.
     */
    private val trackedStream: Flow<ServerEvent> =
        flow {
            totalStarts.incrementAndGet()
            val current = activeCollectors.incrementAndGet()
            maxConcurrentCollectors.updateAndGet { existing -> maxOf(existing, current) }
            try {
                awaitCancellation()
            } finally {
                activeCollectors.decrementAndGet()
                lastCollectionWasCancelled.set(coroutineContext[Job]?.isCancelled == true)
            }
        }

    @BeforeTest
    fun setUp() {
        every { sessionManager.getAccessToken() } returns "valid-token"
        every { messagingClient.messageStream(any()) } returns trackedStream
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.buildManager(): RealtimeManager {
        // Matches production's AppModule-provided `@ApplicationScope`, which is backed by a
        // SupervisorJob so one child failure doesn't cascade-cancel its siblings.
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return RealtimeManager(
            appContext = appContext,
            messagingClient = messagingClient,
            sessionManager = sessionManager,
            messageDao = messageDao,
            reactionDao = reactionDao,
            receiveMessageUseCase = receiveMessageUseCase,
            incomingCallEvents = incomingCallEvents,
            sendPresence = sendPresence,
            appScope = scope,
        )
    }

    // --- Call events reach the call engine, and a failing one does not stop the stream -------

    @Test
    fun `call offers and lifecycle events are delivered to the call engine`() =
        runTest {
            val offer =
                CallOfferEvent(
                    callId = "call-1",
                    callerId = "alice",
                    callType = "video",
                    encryptedSdpPayload = byteArrayOf(1, 2),
                    callerDevice = 2,
                )
            val lifecycle = CallLifecycleEvent(callId = "call-1", peerId = "alice", eventType = "accepted")
            every { messagingClient.messageStream(any()) } returns
                flowOf(ServerEvent.CallOffer(offer), ServerEvent.CallLifecycle(lifecycle))

            buildManager().enterForeground()
            runCurrent()

            coVerify {
                incomingCallEvents.onCallOffer(
                    match {
                        it.callId == "call-1" &&
                            it.callerId == "alice" &&
                            it.callType == "video" &&
                            it.callerDevice == 2 &&
                            it.encryptedSdpPayload.contentEquals(byteArrayOf(1, 2))
                    },
                )
            }
            coVerify { incomingCallEvents.onCallLifecycle(CallLifecycleSignal("call-1", "alice", "accepted")) }
        }

    @Test
    fun `a call engine failure on one offer does not stop later events`() =
        runTest {
            coEvery { incomingCallEvents.onCallOffer(any()) } throws IllegalStateException("boom")
            every { messagingClient.messageStream(any()) } returns
                flowOf(
                    ServerEvent.CallOffer(CallOfferEvent(callId = "bad")),
                    ServerEvent.CallLifecycle(CallLifecycleEvent(callId = "c2", peerId = "p", eventType = "ended")),
                )

            buildManager().enterForeground()
            runCurrent()

            coVerify { incomingCallEvents.onCallLifecycle(CallLifecycleSignal("c2", "p", "ended")) }
        }

    // --- Test 1: the bug -------------------------------------------------------------------

    @Test
    fun `fast app-switch within the drain window leaves the stream running`() =
        runTest {
            val manager = buildManager()

            manager.enterForeground()
            runCurrent()
            assertEquals(1, activeCollectors.get(), "precondition: stream should be collecting")

            manager.enterBackground()
            advanceTimeBy(100)
            manager.enterForeground()
            advanceTimeBy(200)
            runCurrent()

            assertEquals(
                1,
                activeCollectors.get(),
                "a foreground inside the 200ms drain window must keep the stream alive",
            )
        }

    // --- Test 2: ordinary background still stops -------------------------------------------

    @Test
    fun `backgrounding with no return stops the stream`() =
        runTest {
            val manager = buildManager()

            manager.enterForeground()
            runCurrent()
            assertEquals(1, activeCollectors.get(), "precondition: stream should be collecting")

            manager.enterBackground()
            advanceTimeBy(201)
            runCurrent()

            assertEquals(0, activeCollectors.get(), "an uninterrupted background should stop the stream")
        }

    // --- Test 3: defect B -------------------------------------------------------------------

    @Test
    fun `a superseded stream's cleanup never kills the job that replaced it`() =
        runTest {
            val manager = buildManager()

            // Start stream A.
            manager.enterForeground()
            runCurrent()
            assertEquals(1, activeCollectors.get(), "precondition: stream A should be collecting")

            // Stop A synchronously (token-empty path calls stopStream() inline, before A's
            // own finally block has had a chance to run on the dispatcher) and immediately
            // start B in its place, all before yielding back to the scheduler.
            every { sessionManager.getAccessToken() } returns null
            manager.enterBackground()
            every { sessionManager.getAccessToken() } returns "valid-token"
            manager.enterForeground()

            // Now let A's cancellation resume (running its finally) and let B start.
            runCurrent()

            // A dead job's cleanup should not be able to erase a live job's handle. Trigger
            // another foreground transition the way a real fast app-switch would, and prove
            // it does not spin up a second, redundant collector alongside the live one.
            manager.enterForeground()
            runCurrent()

            assertEquals(
                1,
                maxConcurrentCollectors.get(),
                "at most one collector should ever be active at the same time",
            )
        }

    // --- Test 4: defect C -------------------------------------------------------------------

    @Test
    fun `an ordinary stop is not logged as a stream failure`() =
        runTest {
            val manager = buildManager()

            manager.enterForeground()
            runCurrent()

            manager.enterBackground()
            advanceTimeBy(201)
            runCurrent()

            verify(exactly = 0) { Log.e(any(), any(), any()) }
            assertTrue(
                lastCollectionWasCancelled.get(),
                "an ordinary stop should complete the stream job cancelled, not normally",
            )
        }

    // --- Test 5: repeated backgrounding schedules at most one pending stop ------------------

    @Test
    fun `repeated backgrounding schedules no more than one pending stop`() =
        runTest {
            val manager = buildManager()

            manager.enterForeground()
            runCurrent()
            assertEquals(1, activeCollectors.get(), "precondition: stream should be collecting")

            manager.enterBackground() // schedules stop #1, due at t=200
            advanceTimeBy(100) // t=100
            manager.enterBackground() // should supersede stop #1, due at t=300 - not stack a second one
            advanceTimeBy(100) // t=200 - stop #1's original deadline
            runCurrent()

            assertEquals(
                1,
                activeCollectors.get(),
                "the superseded first pending stop must not fire at its original t=200 deadline",
            )

            advanceTimeBy(101) // t=301 - the second pending stop's deadline
            runCurrent()

            assertEquals(0, activeCollectors.get(), "the surviving pending stop should still fire")
        }

    // --- Call wake: the stream opens from the background and closes after the window --------

    @Test
    fun `a call wake opens the stream in the background and closes it after the ring window`() =
        runTest {
            val manager = buildManager()
            manager.wakeForCall("call-1")
            runCurrent()
            assertEquals(1, activeCollectors.get(), "call wake must open the stream")

            advanceTimeBy(RealtimeManager.CALL_WAKE_WINDOW_MS - 1_000)
            runCurrent()
            assertEquals(1, activeCollectors.get(), "still open inside the window")

            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(0, activeCollectors.get(), "closed once the window is over")
        }

    @Test
    fun `a foreground during the window keeps the stream, and backgrounding inside the window does not close it`() =
        runTest {
            val manager = buildManager()
            manager.wakeForCall("call-1")
            runCurrent()
            manager.enterForeground()
            runCurrent()
            advanceTimeBy(RealtimeManager.CALL_WAKE_WINDOW_MS + 1_000)
            runCurrent()
            assertEquals(1, activeCollectors.get(), "foreground owns the stream now")

            val second = buildManager()
            second.enterForeground()
            runCurrent()
            second.wakeForCall("call-2")
            second.enterBackground()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, activeCollectors.get(), "the wake window outlives a background transition")
        }

    // --- Presence: tracked peers hear ONLINE on foreground and every 30 s, OFFLINE on background --

    @Test
    fun `a tracked peer is told ONLINE on foreground, re-announced every 30 seconds, and OFFLINE on background`() =
        runTest {
            val manager = buildManager()
            manager.trackPresencePeer("alice")
            manager.enterForeground()
            runCurrent()
            coVerify(exactly = 1) { sendPresence("alice", PresenceStatus.ONLINE) }

            advanceTimeBy(RealtimeManager.PRESENCE_INTERVAL_MS + 100)
            runCurrent()
            coVerify(exactly = 2) { sendPresence("alice", PresenceStatus.ONLINE) }

            manager.enterBackground()
            runCurrent()
            coVerify(exactly = 1) { sendPresence("alice", PresenceStatus.OFFLINE) }
            advanceTimeBy(RealtimeManager.PRESENCE_INTERVAL_MS * 2)
            runCurrent()
            coVerify(exactly = 2) { sendPresence("alice", PresenceStatus.ONLINE) }
        }

    @Test
    fun `tracking a peer while in the foreground announces immediately, and untracking stops the beats`() =
        runTest {
            val manager = buildManager()
            manager.enterForeground()
            runCurrent()
            manager.trackPresencePeer("bob")
            runCurrent()
            coVerify(exactly = 1) { sendPresence("bob", PresenceStatus.ONLINE) }
            manager.untrackPresencePeer("bob")
            advanceTimeBy(RealtimeManager.PRESENCE_INTERVAL_MS + 100)
            runCurrent()
            coVerify(exactly = 1) { sendPresence("bob", PresenceStatus.ONLINE) }
        }

    // --- Reactions land in the reactions table ------------------------------------------------

    @Test
    fun `reaction events add and remove rows in the reactions table`() =
        runTest {
            every { messagingClient.messageStream(any()) } returns
                flowOf(
                    ServerEvent.Reaction(Reaction("m1", "c1", "alice", "❤️", removed = false, timestamp = 5)),
                    ServerEvent.Reaction(Reaction("m1", "c1", "alice", "👍", removed = true, timestamp = 6)),
                    ServerEvent.Reaction(Reaction("", "c1", "alice", "👍", removed = false, timestamp = 7)),
                )

            buildManager().enterForeground()
            runCurrent()

            coVerify(exactly = 1) { reactionDao.upsert(MessageReactionEntity("m1", "alice", "❤️", 5)) }
            coVerify(exactly = 1) { reactionDao.delete("m1", "alice", "👍") }
            coVerify(exactly = 1) { reactionDao.upsert(any()) }
        }
}
