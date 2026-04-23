package com.sanchr.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Test implementation of [DispatcherProvider] that runs every dispatcher
 * (including [signalDispatcher]) on a shared [TestCoroutineScheduler] so
 * tests can drive virtual time via `runTest { ... }`.
 *
 * Keeping all five dispatchers on one scheduler preserves the single-threaded
 * serialization guarantee of `signalDispatcher` while making libsignal-touching
 * code deterministic and instantly executable in tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : DispatcherProvider {
    override val main: CoroutineDispatcher = StandardTestDispatcher(scheduler, name = "test-main")
    override val io: CoroutineDispatcher = StandardTestDispatcher(scheduler, name = "test-io")
    override val default: CoroutineDispatcher = StandardTestDispatcher(scheduler, name = "test-default")
    override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher(scheduler, name = "test-unconfined")
    override val signalDispatcher: CoroutineDispatcher =
        StandardTestDispatcher(scheduler, name = "test-signal")
}
