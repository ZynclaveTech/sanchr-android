package com.sanchr.core.crypto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sealed-sender interop contract test between iOS and Android.
 *
 * Phase F of the M3 messaging plan. The goal is to prove that an envelope
 * produced by the iOS sealed-sender stack (against the same TrustRoot and
 * backend fixture) round-trips through the Android [com.sanchr.core.crypto.sealed.SealedSenderCipher],
 * and vice versa.
 *
 * Fixtures are injected via BuildConfig fields populated from Gradle
 * properties (see `core/crypto/build.gradle.kts`):
 *  - `sanchr.sealedSenderTrustRoot` → [BuildConfig.SEALED_SENDER_TRUST_ROOT]
 *  - `sanchr.iosTestFixtureEnvelope` → [BuildConfig.IOS_TEST_FIXTURE_ENVELOPE]
 *  - `sanchr.iosTestFixtureExpectedPlaintext` → [BuildConfig.IOS_TEST_FIXTURE_EXPECTED_PLAINTEXT]
 *  - `sanchr.iosTestFixtureTimestamp` → [BuildConfig.IOS_TEST_FIXTURE_TIMESTAMP]
 *  - `sanchr.iosTestFixtureSinkPath` → [BuildConfig.IOS_TEST_FIXTURE_SINK_PATH]
 *
 * In a default build none of those Gradle properties are set, so every test
 * in this class skips at runtime via [assumeTrue]. The class still compiles
 * and links against the androidTest source set — that is the compile-only
 * bar for Phase F per the plan (no emulator available in this environment).
 */
@RunWith(AndroidJUnit4::class)
class SealedSenderInteropContractTest {
    @Test
    fun android_decrypts_ios_produced_sealed_envelope() {
        runBlocking {
            val fixture = BuildConfig.IOS_TEST_FIXTURE_ENVELOPE
            val expected = BuildConfig.IOS_TEST_FIXTURE_EXPECTED_PLAINTEXT
            val timestamp = BuildConfig.IOS_TEST_FIXTURE_TIMESTAMP
            val trustRoot = BuildConfig.SEALED_SENDER_TRUST_ROOT

            assumeTrue("IOS_TEST_FIXTURE_ENVELOPE not set — skipping", fixture.isNotEmpty())
            assumeTrue("SEALED_SENDER_TRUST_ROOT not set — skipping", trustRoot.isNotEmpty())
            assumeTrue(
                "IOS_TEST_FIXTURE_EXPECTED_PLAINTEXT not set — skipping",
                expected.isNotEmpty(),
            )
            assumeTrue("IOS_TEST_FIXTURE_TIMESTAMP not set — skipping", timestamp > 0L)

            @Suppress("UNUSED_VARIABLE")
            val envelopeBytes = Base64.decode(fixture, Base64.NO_WRAP)

            // TODO(ROUND-TRIP-FIXTURE): wire the full receiver-side crypto stack
            //   identical to [RoundTripIntegrationTest.buildStack] and drive
            //   [com.sanchr.core.crypto.sealed.SealedSenderCipher.sealedDecrypt]
            //   at `envelopeBytes`. Concretely:
            //     1. Construct a [DatabasePassphraseProvider] + SQLCipher-backed
            //        [SanchrDatabase] for the receiver identity that matches the
            //        iOS-side fixture's recipient (same userUuid, deviceId,
            //        registrationId, identity key pair — the iOS harness MUST
            //        publish these alongside the envelope capture).
            //     2. Build [SanchrIdentityKeyStore], [SanchrPreKeyStore],
            //        [SanchrSignedPreKeyStore], [SanchrKyberPreKeyStore],
            //        [SanchrSessionStore], [SanchrSenderKeyStore], and
            //        [SanchrSignalProtocolStore] around it.
            //     3. Pre-load the receiver's SPK / OPK / Kyber records that the
            //        iOS sender consumed when producing `envelopeBytes`, so the
            //        X3DH + PQXDH handshake embedded in the sealed inner
            //        PreKeySignalMessage can complete locally.
            //     4. Instantiate [SealedSenderCipher] with a
            //        [BuildConfigTrustRootProvider] (already Phase A).
            //     5. Call sealedDecrypt(envelopeBytes, timestamp) and compare
            //        result.plaintext against `expected` (UTF-8).
            //
            //   When wiring this, extract a shared helper
            //   `IntegrationTestStack.kt` under
            //   core/crypto/src/androidTest/java/com/sanchr/core/crypto/testhelpers/
            //   and migrate [RoundTripIntegrationTest] to use it — the receiver
            //   stack wiring is otherwise duplicated verbatim.
            //
            //   Deferred here because (a) this environment is compile-only
            //   (no emulator) so the test cannot run end-to-end, and (b) the
            //   iOS fixture contract (the exact set of pre-key records + cert
            //   embedded in the envelope) is still being finalized on the iOS
            //   side. The assumeTrue guards above guarantee this TODO is
            //   unreachable in every current build configuration.
            val result: ByteArray =
                TODO(
                    "wire receiver stack per RoundTripIntegrationTest + SealedSenderCipher.sealedDecrypt",
                )

            @Suppress("UNREACHABLE_CODE")
            assertEquals(expected, String(result))
        }
    }

    @Test
    fun android_produces_sealed_envelope_ios_can_decrypt() {
        runBlocking {
            val sinkPath = BuildConfig.IOS_TEST_FIXTURE_SINK_PATH
            val trustRoot = BuildConfig.SEALED_SENDER_TRUST_ROOT

            assumeTrue("IOS_TEST_FIXTURE_SINK_PATH not set — skipping", sinkPath.isNotEmpty())
            assumeTrue("SEALED_SENDER_TRUST_ROOT not set — skipping", trustRoot.isNotEmpty())

            // TODO(ROUND-TRIP-FIXTURE): build a sender stack that mirrors
            //   [RoundTripIntegrationTest.buildStack], establish a session
            //   against the iOS recipient's published pre-key bundle (fed in
            //   via an additional fixture Gradle property — recipient userId,
            //   deviceId, identity+SPK+OPK+Kyber), then:
            //     1. Encrypt the canonical interop plaintext "sanchr-interop-v1"
            //        with [SignalSessionManager.encrypt] so sealed-sender is
            //        used end-to-end (requires a valid sender certificate
            //        minted by the backend fixture).
            //     2. Base64-encode the ciphertext (NO_WRAP) and write it to
            //        `sinkPath`, which the iOS test harness reads and feeds
            //        into its own sealed-sender decrypt.
            //   Extract into IntegrationTestStack.aliceEncryptsTo(recipient)
            //   when the helper lands.
            TODO("wire sender stack + write base64 ciphertext to sinkPath for iOS consumer")
        }
    }
}
