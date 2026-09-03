package com.sanchr.core.crypto.sealed

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.protocol.ecc.ECPublicKey

/**
 * Wire-format oracle: proves that the **backend** Rust [`SealedSenderSigner`]
 * (sanchr-server-crypto/src/sealed_sender.rs) emits bytes that libsignal's own
 * `SenderCertificate` decoder + `CertificateValidator` accept end-to-end.
 *
 * Without this test the backend rewrite from `ed25519-dalek` to libsignal-canonical
 * XEdDSA over Curve25519 would have to be validated by hand against a running
 * signing server — slow and unreliable. Here we embed deterministic fixtures
 * produced by the Rust test
 * `sealed_sender::tests::emit_kotlin_oracle_fixture` and run them through the
 * exact same libsignal call sites the production [SealedSenderCipher] uses.
 *
 * **Regenerating the fixtures** (only needed if the Rust signer's wire format
 * changes — proto field numbers, key encoding, signing scheme):
 * ```
 * cargo test -p sanchr-server-crypto sealed_sender::tests::emit_kotlin_oracle_fixture -- --nocapture
 * ```
 * Paste the four `KOTLIN_FIXTURE_*` lines into the constants below.
 */
class SealedSenderInteropFixtureTest {
    @Before
    fun setUp() {
        // android.util.Base64 has no JVM impl in unit tests; route to java.util.Base64.
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64
                .getDecoder()
                .decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `libsignal SenderCertificate decoder accepts backend-issued bytes`() {
        val trustRootBytes =
            java.util.Base64
                .getDecoder()
                .decode(TRUST_ROOT_B64)
        val certBytes =
            java.util.Base64
                .getDecoder()
                .decode(CERT_B64)
        val expectedIdentityKeyBytes =
            java.util.Base64
                .getDecoder()
                .decode(IDENTITY_KEY_B64)

        // 1. Trust root must parse as a libsignal Curve25519 ECPublicKey
        //    (33 bytes, type byte 0x05). If the backend ever drifts back to
        //    raw ed25519, this throws InvalidKeyException — the same crash
        //    we used to hit on production.
        val trustRoot = ECPublicKey(trustRootBytes)

        // 2. Decode the sender certificate. This is the call that previously
        //    failed with "bad key type <0x3c>" — the integration regression
        //    this whole rewrite chain is gated on.
        val cert = SenderCertificate(certBytes)

        assertEquals("11111111-2222-3333-4444-555555555555", cert.senderUuid)
        assertEquals(1, cert.senderDeviceId)
        assertEquals(EXPECTED_EXPIRES_UNIX_MS, cert.expiration)
        assertNotNull(cert.signer)
        assertEquals(7, cert.signer.keyId)
        assertEquals(
            expectedIdentityKeyBytes.toList(),
            cert.key.serialize().toList(),
        )

        // 3. Validate signatures. CertificateValidator runs the same XEdDSA
        //    verification path that production decrypt uses — mirroring
        //    SealedSenderCipher.sealedDecrypt's CertificateValidator(trustRoot).
        //    Validate `1ms before expiry` so the cert is unambiguously in-window
        //    at test time (fixture's expiry is fixed at 1777284855, year 2026).
        val validator = CertificateValidator(trustRoot)
        validator.validate(cert, EXPECTED_EXPIRES_UNIX_MS - 1L)
    }

    private companion object {
        // Fixtures emitted by sanchr-server-crypto::sealed_sender::tests::emit_kotlin_oracle_fixture.
        // Seed: 0x11 * 32, key_id: 7, sender: 11111111-...-555555555555, device 1.
        private const val TRUST_ROOT_B64 = "BXtOkJu+f/5ExGWiIAN9YI7jWJfTHvly8H90iSyw9z8T"
        private const val CERT_B64 =
            "Cr8BEAEZ9zbvaQAAAAAiIQU3bqXcE0qBuO8mXZTLAjlwp94VTIO68Shfls0EO3Kp4Cpp" +
                "CiUIBxIhBXtOkJu+f/5ExGWiIAN9YI7jWJfTHvly8H90iSyw9z8TEkDqwTRqSVG9/R6I" +
                "fcfQI6vURj6UiGWW9+Qz3GXQVlSwM1W2bpqDaSnspUuWeG3Cvo5ldegLge31YYy5qPz/" +
                "Z+WKMiQxMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1NTUSQJnwy3nGbWne" +
                "lxz3/lX/bDWDCGH7VWMK3/JT2IsJBzTwYQncWI3LcHkXtWhLPLIGeYRngAmHkMFbcitu" +
                "U5td4YU="
        private const val IDENTITY_KEY_B64 = "BTdupdwTSoG47yZdlMsCOXCn3hVMg7rxKF+WzQQ7cqng"

        // Backend emits expiration in seconds; libsignal-android stores it in
        // milliseconds (parses the proto's uint64 verbatim, then the cert proto
        // happens to use ms in Signal's deployment). Our backend's proto field
        // is uint64 seconds (sealed_sender.proto Certificate.expires); so we
        // compare against the raw seconds value libsignal hands back.
        private const val EXPECTED_EXPIRES_UNIX_MS = 1777284855L
    }
}
