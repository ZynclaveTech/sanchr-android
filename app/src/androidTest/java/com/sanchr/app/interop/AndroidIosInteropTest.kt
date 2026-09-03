package com.sanchr.app.interop

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Annotation tag for end-to-end Android↔iOS interop tests.
 *
 * Tests carrying this annotation are filtered in by the
 * `nightly-interop.yml` workflow (`-Pandroid.testInstrumentationRunnerArguments.annotation=com.sanchr.app.interop.InteropTest`),
 * which also boots the `sanchr-backend-oss` docker-compose harness and
 * passes the harness URL via `backendUrl` runner argument. Outside that
 * CI path the tests gracefully skip rather than fail — no developer
 * machine should have to boot the whole backend to run `check`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class InteropTest

/**
 * Android↔iOS sealed-sender interop verification.
 *
 * Contract this test enforces (spec §5 risks table, M6 plan Phase 5):
 *
 *  1. The Android client can register a fresh account against a live
 *     backend instance and upload its identity + prekey bundle.
 *  2. Given a pre-baked iOS-generated sealed-sender envelope (bytes
 *     produced by the iOS test suite and shipped as a test fixture),
 *     the Android decryption pipeline decrypts + persists the message.
 *  3. A message sent Android→iOS is accepted by the server's send RPC
 *     and acked.
 *  4. Logout wipes the local DB and key material.
 *
 * Steps (2)–(4) are the interop contract — if they break, Android and
 * iOS have diverged on the libsignal version or sealed-sender cert format.
 *
 * ## Why the heavy body is currently guarded
 *
 * The full test requires:
 *   - A running `sanchr-backend-oss` harness reachable from the emulator
 *     (the nightly CI job brings one up via docker-compose).
 *   - Golden iOS sealed-sender envelope bytes in
 *     `app/src/androidTest/assets/ios-fixtures/` — these are regenerated
 *     by the iOS XCTest interop suite and copied into this repo whenever
 *     the wire format changes (see that directory's README).
 *
 * When either prerequisite is missing (local `connectedDebugAndroidTest`
 * without the compose harness, or a dev snapshot that predates the
 * fixture handoff), the test skips via JUnit `Assume` rather than failing.
 * The skeleton exercises the reachability + fixture-presence preconditions
 * so a regression in those shows up as a SKIP reason, not a silent no-op.
 */
@LargeTest
@InteropTest
@RunWith(AndroidJUnit4::class)
class AndroidIosInteropTest {
    private lateinit var backendUrl: String
    private var fixturePresent: Boolean = false

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        backendUrl = args.getString("backendUrl", DEFAULT_BACKEND_URL)
        assumeTrue(
            "Interop test requires a reachable backend at $backendUrl; skip.",
            isReachable(backendUrl),
        )

        val ctx = InstrumentationRegistry.getInstrumentation().context
        fixturePresent =
            runCatching {
                ctx.assets.open(IOS_ENVELOPE_FIXTURE).use { it.available() > 0 }
            }.getOrDefault(false)
        assumeTrue(
            "iOS sealed-sender fixture missing at assets/$IOS_ENVELOPE_FIXTURE; " +
                "see app/src/androidTest/assets/ios-fixtures/README.md.",
            fixturePresent,
        )
    }

    /**
     * The full interop round-trip. Implementation lands once the backend
     * harness exposes a test-only registration path and the iOS fixture
     * handoff is in place (tracked in the M6 plan self-review). Until
     * then this stub fires as a deliberate TODO so the nightly CI job
     * visibly reports "pending implementation" rather than a false green.
     */
    @Test
    fun androidIosSealedSenderRoundTrip() {
        // Phase 5 ships the harness. The round-trip assertions land in a
        // follow-up PR on the same branch before M6 exit (plan §Exit).
        // We still exercise the preconditions in @Before so any regression
        // in fixture shipping or backend reachability surfaces here.
        throw UnsupportedOperationException(
            "Interop round-trip not yet implemented. See the M6 Phase 5 " +
                "self-review in docs/superpowers/plans/2026-04-24-android-m6-hardening.md " +
                "for the exit-gate checklist.",
        )
    }

    private fun isReachable(url: String): Boolean =
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000
            conn.readTimeout = 2_000
            conn.requestMethod = "HEAD"
            // gRPC servers commonly return 405 / 415 to a raw HTTP HEAD, but
            // any response at all means the TCP + HTTP layer reached a
            // listener — good enough as a reachability probe.
            try {
                conn.responseCode
                true
            } catch (_: IOException) {
                false
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

    private companion object {
        /** Emulator-to-host loopback; nightly CI overrides via runner arg. */
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:50051"

        /** Relative to `app/src/androidTest/assets/`. */
        const val IOS_ENVELOPE_FIXTURE = "ios-fixtures/sealed-envelope-v1.bin"
    }
}
