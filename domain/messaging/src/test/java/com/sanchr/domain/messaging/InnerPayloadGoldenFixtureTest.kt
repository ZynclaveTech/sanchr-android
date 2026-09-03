package com.sanchr.domain.messaging

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Pins the exact bytes Android's [InnerPayload] encoder produces for one
 * fixed, fully-populated input against a committed fixture file.
 *
 * This proves Android's encoder is self-consistent release over release and
 * that its key names have not drifted (e.g. a rename applied on both sides
 * of a refactor without anyone noticing). It does **not** prove interop with
 * iOS: no iOS-side test decodes this fixture, because the iOS half of this
 * plan has not landed yet. Until iOS grows a matching test that decodes
 * [FIXTURE_RESOURCE], this only guards Android against itself.
 */
class InnerPayloadGoldenFixtureTest {
    companion object {
        private const val FIXTURE_RESOURCE = "/inner-payload-golden.json"
    }

    // The fixed, fully-populated input the fixture bytes were generated from.
    // `content` and `senderProfileKey` each carry a byte above 0x7F (0xE2/0x9C/0xA8
    // in content, several values >=134 in the key) — the range a sign-extension
    // or wrong-base64-alphabet mistake would corrupt.
    private val goldenPayload =
        InnerPayload(
            conversationId = "conv-golden-1",
            messageId = "msg-golden-1",
            contentType = "text",
            content = byteArrayOf(0x68, 0x69, 0xE2.toByte(), 0x9C.toByte(), 0xA8.toByte(), 0x00),
            isSync = true,
            expiresAfterSecs = 604_800,
            senderProfileKey = ByteArray(32) { (it * 7 + 1).toByte() },
            senderUserId = "user-golden-1",
            senderDeviceId = 2,
            replyToMessageId = "msg-golden-0",
        )

    private fun readFixtureBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(FIXTURE_RESOURCE)) {
            "missing fixture resource $FIXTURE_RESOURCE"
        }.use { it.readBytes() }

    @Test
    fun `encodes the golden payload to the committed fixture bytes`() {
        val fixtureBytes = readFixtureBytes()
        assertContentEquals(fixtureBytes, goldenPayload.encode())
    }

    @Test
    fun `decodes the committed fixture bytes back to the golden payload`() {
        val fixtureBytes = readFixtureBytes()
        val decoded = checkNotNull(InnerPayload.decode(fixtureBytes)) { "fixture failed to decode" }
        assertEquals(goldenPayload, decoded)
    }
}
