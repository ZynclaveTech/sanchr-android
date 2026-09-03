package com.sanchr.sync.realtime

import android.content.Context
import android.util.Log
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.ReceiveMessageUseCase
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.ServerEvent
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
    private val receiveMessageUseCase = mockk<ReceiveMessageUseCase>(relaxed = true)

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
            receiveMessageUseCase = receiveMessageUseCase,
            appScope = scope,
        )
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
}
