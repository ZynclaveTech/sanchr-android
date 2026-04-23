package com.sanchr.core.common

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SignalDispatcherTest {
    @Test
    fun `signalDispatcher runs all work on a single thread`() =
        runBlocking {
            val threads = ConcurrentHashMap.newKeySet<String>()
            val dispatcher = SignalDispatcher.create()
            val jobs =
                (1..32).map {
                    GlobalScope.launch(dispatcher) {
                        // Strip kotlinx coroutine debug suffix (e.g. " @coroutine#N")
                        threads += Thread.currentThread().name.substringBefore(" @")
                    }
                }
            jobs.forEach { it.join() }
            dispatcher.close()
            assertEquals(1, threads.size, "expected a single thread, got: $threads")
        }
}
