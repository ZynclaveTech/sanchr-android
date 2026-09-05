package com.sanchr.proto.contacts

import com.google.protobuf.ByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import sanchr.contacts.Contacts

/**
 * Pins the places where the hand-written models and the proto disagree by
 * name, so the reconciliation cannot silently regress.
 */
class ContactsMappersTest {
    @Test
    fun `phone hashes go on the wire as raw bytes, not hex text`() {
        val hex = "00ff10" + "ab".repeat(29) // 32 bytes
        val proto = SyncContactsRequest(phoneHashes = listOf(hex)).toProto()

        assertEquals(1, proto.phoneHashesCount)
        val bytes = proto.getPhoneHashes(0).toByteArray()
        assertEquals(32, bytes.size)
        assertContentEquals(byteArrayOf(0x00, 0xFF.toByte(), 0x10), bytes.copyOfRange(0, 3))
    }

    @Test
    fun `an odd-length hex hash is rejected before it reaches the wire`() {
        assertFailsWith<IllegalArgumentException> { SyncContactsRequest(phoneHashes = listOf("abc")).toProto() }
    }

    @Test
    fun `sync response maps the proto's matches field to matchedContacts`() {
        val proto =
            Contacts.SyncContactsResponse
                .newBuilder()
                .addMatches(
                    Contacts.MatchedContact
                        .newBuilder()
                        .setUserId("u-1")
                        .setDisplayName("Ada")
                        .setAvatarUrl("https://x/a.png")
                        .setPhoneNumber("+14155551234"),
                ).build()

        val model = proto.toModel()

        assertEquals(1, model.matchedContacts.size)
        with(model.matchedContacts[0]) {
            assertEquals("u-1", userId)
            assertEquals("Ada", displayName)
            assertEquals("https://x/a.png", avatarUrl)
            assertEquals("+14155551234", phoneNumber)
        }
    }

    @Test
    fun `matched contact carries the encrypted profile fields as raw bytes`() {
        val name = ByteArray(28) { 7 }
        val proto =
            Contacts.SyncContactsResponse
                .newBuilder()
                .addMatches(
                    Contacts.MatchedContact
                        .newBuilder()
                        .setUserId("u-1")
                        .setDisplayName("Sanchr User")
                        .setEncryptedDisplayName(ByteString.copyFrom(name))
                        .setStatusText("st"),
                ).build()

        val m = proto.toModel().matchedContacts.single()

        assertContentEquals(name, m.encryptedDisplayName)
        assertEquals(0, m.encryptedBio.size)
        assertEquals("st", m.statusText)
        // The plaintext column is the server's registration placeholder for
        // Profile-Key accounts; it is mapped, but must never be displayed.
        assertEquals("Sanchr User", m.displayName)
    }

    @Test
    fun `contact carries the encrypted profile fields as raw bytes`() {
        val bio = ByteArray(31) { 4 }
        val c =
            Contacts.Contact
                .newBuilder()
                .setUserId("u-1")
                .setEncryptedBio(ByteString.copyFrom(bio))
                .build()
                .toModel()

        assertContentEquals(bio, c.encryptedBio)
        assertEquals(0, c.encryptedDisplayName.size)
    }

    @Test
    fun `models with ciphertext compare by content, not reference`() {
        val a = MatchedContact(userId = "u", encryptedDisplayName = byteArrayOf(1, 2))
        val b = MatchedContact(userId = "u", encryptedDisplayName = byteArrayOf(1, 2))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `block and unblock send the id as contact_user_id`() {
        assertEquals("u-9", BlockContactRequest(userId = "u-9").toProto().contactUserId)
        assertEquals("u-9", UnblockContactRequest(userId = "u-9").toProto().contactUserId)
    }

    @Test
    fun `blocked list of ids maps to contacts marked blocked`() {
        val proto =
            Contacts.GetBlockedListResponse
                .newBuilder()
                .addBlockedUserIds("u-1")
                .addBlockedUserIds("u-2")
                .build()

        val model = proto.toModel()

        assertEquals(listOf("u-1", "u-2"), model.blockedUsers.map { it.userId })
        assertTrue(model.blockedUsers.all { it.isBlocked })
    }

    @Test
    fun `contact maps the fields the wire actually carries`() {
        val proto =
            Contacts.Contact
                .newBuilder()
                .setUserId("u-1")
                .setPhoneNumber("+1")
                .setDisplayName("Ada")
                .setIsBlocked(true)
                .build()

        val c = proto.toModel()

        assertEquals("u-1", c.userId)
        assertEquals("+1", c.phoneNumber)
        assertEquals("Ada", c.displayName)
        assertTrue(c.isBlocked)
    }
}
