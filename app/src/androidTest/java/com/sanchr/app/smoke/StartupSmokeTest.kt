package com.sanchr.app.smoke

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.sanchr.app.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Annotation tag for smoke-level instrumented tests.
 *
 * The CI `instrumented-smoke` job filters to this annotation so the emulator
 * matrix stays fast even as the androidTest suite grows. The full interop
 * suite runs nightly with a different filter (`@InteropTest`) and a booted
 * backend harness.
 *
 * Runtime retention is required — AndroidJUnitRunner reflects over it to
 * gate which tests run when the runner arg
 * `android.testInstrumentationRunnerArguments.annotation=com.sanchr.app.smoke.SmokeTest`
 * is set.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SmokeTest

/**
 * Launches `MainActivity` and asserts it reaches the RESUMED state.
 *
 * Why this is the minimum-viable post-M6 smoke test: if R8 strips a Hilt
 * entry point (`@HiltAndroidApp`, `@AndroidEntryPoint`, or a `@Inject`
 * constructor), the app crashes during component instantiation before
 * `onResume` runs. Reaching RESUMED therefore proves:
 *  - `SanchrApp`'s `@HiltAndroidApp` component generation survived
 *    minification,
 *  - `MainActivity`'s `@AndroidEntryPoint` injection graph resolved,
 *  - The bootstrap VM (`AppBootstrapViewModel`) was injectable, and
 *  - No Compose setup path was stripped (setContent would throw).
 *
 * We deliberately do NOT assert on UI content — network-dependent startup
 * (session check, FCM token refresh, backend reachability) is
 * non-deterministic on CI emulators. Any content assertion belongs in a
 * higher-tier test with a stubbed network.
 */
@LargeTest
@SmokeTest
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {
    /**
     * `MainActivity.onCreate` requests `POST_NOTIFICATIONS` on API 33+. On a
     * fresh CI emulator that puts the system `GrantPermissionsActivity` over
     * the app ~100ms after it resumes, so the activity drops to PAUSED and
     * `moveToState(RESUMED)` times out (API 34 job, run 33929615531). API 28
     * never saw it: the permission does not exist there. Grant it up front,
     * the way a user who already answered the prompt would have.
     */
    @Before
    fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @Test
    fun mainActivityReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNotNull("MainActivity handle should be non-null once RESUMED", activity)
            }
        }
    }
}
