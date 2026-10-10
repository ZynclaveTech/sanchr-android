package com.sanchr.core.crypto.sealed

import com.sanchr.core.crypto.MessagePadding
import java.io.File
import java.util.Base64
import java.util.Optional
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.libsignal.metadata.SealedSessionCipher
import org.signal.libsignal.metadata.certificate.CertificateValidator
import org.signal.libsignal.metadata.certificate.ServerCertificate
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore

/**
 * The envelope this app sends, as bytes on disk for the iOS test suite to
 * open: `interop/android-sealed-v1.json`, copied to `Tests/fixtures/` in the
 * iOS repo and read there by `CrossPlatformFixtureTests`.
 *
 * The two apps were unable to decrypt each other's messages and nothing
 * noticed, because the only interop check was a nightly job that never got
 * past its first step. A file produced by one platform's build of libsignal
 * and opened by the other's, in each repo's ordinary test run, is a check
 * that cannot quietly stop running.
 *
 * To regenerate (only when the envelope this app sends changes):
 *
 *     SANCHR_WRITE_FIXTURES=1 ./gradlew :core:crypto:testDebugUnitTest --tests "*AndroidToIosFixtureTest*"
 *
 * then copy the file to the iOS repo. Every key in it was made for the
 * fixture and exists nowhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class AndroidToIosFixtureTest {
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    @Test
    fun `the checked-in envelope is one this app's own libsignal opens`() {
        // When regenerating, read back the file just written: the copy on
        // the test classpath was taken before this ran.
        val text =
            if (System.getenv("SANCHR_WRITE_FIXTURES") == "1") {
                writeFixture().readText()
            } else {
                checkNotNull(javaClass.getResource("/interop/$FILE_NAME")) { "missing $FILE_NAME" }.readText()
            }
        val fixture = JSONObject(text)
        val recipient = fixture.getJSONObject("recipient")
        val store = InMemorySignalProtocolStore(IdentityKeyPair(bytes(recipient, "identityKeyPair")), recipient.getInt("registrationId"))
        store.storePreKey(recipient.getInt("preKeyId"), PreKeyRecord(bytes(recipient, "preKey")))
        store.storeSignedPreKey(recipient.getInt("signedPreKeyId"), SignedPreKeyRecord(bytes(recipient, "signedPreKey")))
        store.storeKyberPreKey(recipient.getInt("kyberPreKeyId"), KyberPreKeyRecord(bytes(recipient, "kyberPreKey")))

        val opened =
            SealedSessionCipher(store, UUID.fromString(recipient.getString("userId")), null, recipient.getInt("deviceId"))
                .decrypt(
                    CertificateValidator(ECPublicKey(bytes(fixture, "trustRoot"))),
                    bytes(fixture, "envelope"),
                    fixture.getLong("receivedAtMs"),
                )

        val sender = fixture.getJSONObject("sender")
        assertEquals(sender.getString("userId"), opened.senderUuid)
        assertEquals(sender.getInt("deviceId"), opened.deviceId)
        assertContentEquals(bytes(fixture, "plaintext"), MessagePadding.strip(opened.paddedMessage))
        assertEquals(0x11.toByte(), bytes(fixture, "envelope")[0])
    }

    private fun bytes(
        json: JSONObject,
        key: String,
    ): ByteArray = unb64.decode(json.getString(key))

    /** Builds an envelope the way the send path does: pad, encrypt for the session, seal under a certificate. */
    private fun writeFixture(): File {
        val receivedAt = RECEIVED_AT_MS
        val trustRoot = ECKeyPair.generate()
        val server = ECKeyPair.generate()
        val serverCertificate = ServerCertificate(trustRoot.privateKey, 1, server.publicKey)

        val recipientIdentity = IdentityKeyPair.generate()
        val oneTime = ECKeyPair.generate()
        val signed = ECKeyPair.generate()
        val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signedSignature = recipientIdentity.privateKey.calculateSignature(signed.publicKey.serialize())
        val kyberSignature = recipientIdentity.privateKey.calculateSignature(kyber.publicKey.serialize())
        val bundle =
            PreKeyBundle(
                RECIPIENT_REGISTRATION,
                1,
                PRE_KEY_ID,
                oneTime.publicKey,
                SIGNED_PRE_KEY_ID,
                signed.publicKey,
                signedSignature,
                recipientIdentity.publicKey,
                KYBER_PRE_KEY_ID,
                kyber.publicKey,
                kyberSignature,
            )

        val senderIdentity = IdentityKeyPair.generate()
        val senderStore = InMemorySignalProtocolStore(senderIdentity, 1234)
        val recipientAddress = SignalProtocolAddress(RECIPIENT_ID, 1)
        SessionBuilder(senderStore, recipientAddress).process(bundle)
        val certificate =
            serverCertificate.issue(
                server.privateKey,
                SENDER_ID,
                Optional.empty<String>(),
                SENDER_DEVICE,
                senderIdentity.publicKey.publicKey,
                CERTIFICATE_EXPIRES_MS,
            )
        val plaintext = PLAINTEXT.toByteArray()
        val envelope =
            SealedSessionCipher(senderStore, UUID.fromString(SENDER_ID), null, SENDER_DEVICE)
                .encrypt(recipientAddress, certificate, MessagePadding.pad(plaintext))

        val json =
            JSONObject()
                .put("description", "A sealed-sender first message as the Android app sends it. Test keys only.")
                .put("format", "sealed-sender")
                .put("trustRoot", b64.encodeToString(trustRoot.publicKey.serialize()))
                .put("receivedAtMs", receivedAt)
                .put("sender", JSONObject().put("userId", SENDER_ID).put("deviceId", SENDER_DEVICE))
                .put(
                    "recipient",
                    JSONObject()
                        .put("userId", RECIPIENT_ID)
                        .put("deviceId", 1)
                        .put("registrationId", RECIPIENT_REGISTRATION)
                        .put("identityKeyPair", b64.encodeToString(recipientIdentity.serialize()))
                        .put("preKeyId", PRE_KEY_ID)
                        .put("preKey", b64.encodeToString(PreKeyRecord(PRE_KEY_ID, oneTime).serialize()))
                        .put("signedPreKeyId", SIGNED_PRE_KEY_ID)
                        .put(
                            "signedPreKey",
                            b64.encodeToString(SignedPreKeyRecord(SIGNED_PRE_KEY_ID, receivedAt, signed, signedSignature).serialize()),
                        ).put("kyberPreKeyId", KYBER_PRE_KEY_ID)
                        .put(
                            "kyberPreKey",
                            b64.encodeToString(KyberPreKeyRecord(KYBER_PRE_KEY_ID, receivedAt, kyber, kyberSignature).serialize()),
                        ),
                ).put("plaintext", b64.encodeToString(plaintext))
                .put("envelope", b64.encodeToString(envelope))
        val target = File("src/test/resources/interop/$FILE_NAME")
        target.parentFile.mkdirs()
        target.writeText(json.toString(2) + "\n")
        return target
    }

    private companion object {
        const val FILE_NAME = "android-sealed-v1.json"
        const val SENDER_ID = "11111111-2222-4333-8444-555555555555"
        const val SENDER_DEVICE = 2
        const val RECIPIENT_ID = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        const val RECIPIENT_REGISTRATION = 4242
        const val PRE_KEY_ID = 7
        const val SIGNED_PRE_KEY_ID = 42
        const val KYBER_PRE_KEY_ID = 99

        // 2027-01-15, and a certificate good until 2100: the fixture is read
        // at a fixed moment, so it does not expire with the calendar.
        const val RECEIVED_AT_MS = 1_800_000_000_000L
        const val CERTIFICATE_EXPIRES_MS = 4_102_444_800_000L
        const val PLAINTEXT =
            """{"v":1,"conversation_id":"fixture-conversation","message_id":"fixture-message-1",""" +
                """"content_type":"text","content":"aGVsbG8gZnJvbSBhbmRyb2lk","is_sync":false,""" +
                """"sender_user_id":"11111111-2222-4333-8444-555555555555","sender_device_id":2}"""
    }
}
