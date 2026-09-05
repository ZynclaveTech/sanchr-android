package com.sanchr.app.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.sanchr.sync.MessageDrainWorker
import com.sanchr.sync.SendRetryWorker
import com.sanchr.sync.SyncWorker
import com.sanchr.sync.rotation.PreKeyReplenishWorker
import com.sanchr.sync.rotation.SenderCertificateRotationWorker
import com.sanchr.sync.rotation.SignedPreKeyRotationWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every `@HiltWorker` must be constructible through the factory the app
 * actually hands WorkManager (`SanchrApp.workManagerConfiguration`).
 *
 * This is the on-device check for a failure that compiles clean: if the
 * module declaring a worker does not run `androidx.hilt:hilt-compiler`,
 * `@HiltWorker` generates nothing, `HiltWorkerFactory` returns null for the
 * class, and WorkManager falls back to reflection on a
 * `(Context, WorkerParameters)` constructor that an `@AssistedInject` worker
 * does not have:
 *
 *   E WM-WorkerFactory: Could not instantiate com.sanchr.sync.SyncWorker
 *   E WM-WorkerFactory: java.lang.NoSuchMethodException: ...SyncWorker.<init>
 *
 * That was the state of `main` until the compiler was added to `:sync`;
 * background sync, send retry, message drain and all three key-rotation
 * workers had never run. `TestListenableWorkerBuilder` with the app's own
 * factory reproduces exactly that lookup, so this test fails the same way
 * the device would.
 */
@SmokeTest
@RunWith(AndroidJUnit4::class)
class HiltWorkersSmokeTest {
    private val workers: List<Class<out ListenableWorker>> =
        listOf(
            SyncWorker::class.java,
            SendRetryWorker::class.java,
            MessageDrainWorker::class.java,
            SignedPreKeyRotationWorker::class.java,
            SenderCertificateRotationWorker::class.java,
            PreKeyReplenishWorker::class.java,
        )

    @Test
    fun everyHiltWorkerIsConstructibleThroughTheAppsWorkerFactory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext
        if (app !is Configuration.Provider) {
            fail("Application does not provide a WorkManager Configuration; the default initializer would be used")
            return
        }
        val factory = app.workManagerConfiguration.workerFactory

        val failures =
            workers.mapNotNull { cls ->
                runCatching {
                    TestListenableWorkerBuilder
                        .from(context, cls)
                        .setWorkerFactory(factory)
                        .build()
                }.exceptionOrNull()?.let { "${cls.simpleName}: $it" }
            }

        assertEquals("workers the app's factory could not build:\n${failures.joinToString("\n")}", 0, failures.size)
    }
}
