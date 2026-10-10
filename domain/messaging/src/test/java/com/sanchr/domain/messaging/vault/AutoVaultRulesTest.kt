package com.sanchr.domain.messaging.vault

import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.core.model.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * What Auto-Vault Incoming may move. The vault keeps things, so the cases
 * that matter most are the ones it must leave alone.
 */
class AutoVaultRulesTest {
    private val on = ChatVaultPolicy(autoVaultIncoming = true)

    private fun attachment(
        name: String = "a.jpg",
        viewOnce: Boolean? = null,
    ) = MediaAttachment(url = "sanchr-media://$name", mimeType = "image/jpeg", filename = name, isViewOnce = viewOnce)

    private fun image(attachment: MediaAttachment? = attachment()) =
        MessageContent.Image(url = "u", thumbnailUrl = null, width = 1, height = 1, attachment = attachment)

    private fun message(
        content: MessageContent,
        sender: String = "peer",
        expiresAt: Instant? = null,
    ) = Message(
        id = "m1",
        conversationId = "c1",
        senderId = sender,
        content = content,
        status = MessageStatus.DELIVERED,
        timestamp = Instant.fromEpochMilliseconds(1),
        expiresAt = expiresAt,
    )

    @Test
    fun `a received photo is vaulted when the chat asks for it`() {
        assertEquals(listOf("a.jpg"), AutoVaultRules.attachmentsToVault(message(image()), "self", on).map { it.filename })
    }

    @Test
    fun `nothing is vaulted while the switch is off`() {
        assertTrue(AutoVaultRules.attachmentsToVault(message(image()), "self", ChatVaultPolicy.DEFAULT).isEmpty())
        // The other two switches are not this one.
        val others = ChatVaultPolicy(viewOnceOutgoing = true, captureProtection = true)
        assertTrue(AutoVaultRules.attachmentsToVault(message(image()), "self", others).isEmpty())
    }

    @Test
    fun `what this account sent is not vaulted, including from another device`() {
        assertTrue(AutoVaultRules.attachmentsToVault(message(image(), sender = "self"), "self", on).isEmpty())
    }

    @Test
    fun `view-once media is never vaulted`() {
        // It was shown once on purpose. Vaulting it would keep it for good.
        val once = message(image(attachment(viewOnce = true)))
        assertTrue(AutoVaultRules.attachmentsToVault(once, "self", on).isEmpty())
    }

    @Test
    fun `disappearing media is never vaulted`() {
        val timed = message(image(), expiresAt = Instant.fromEpochMilliseconds(99))
        assertTrue(AutoVaultRules.attachmentsToVault(timed, "self", on).isEmpty())
    }

    @Test
    fun `an attachment that is a plain link is never fetched for the vault`() {
        // Vaulting downloads with nobody tapping anything; a URL of the
        // sender's choosing must not be requested the moment it arrives.
        val link = MediaAttachment(url = "https://example.com/a.jpg", mimeType = "image/jpeg")
        assertTrue(AutoVaultRules.attachmentsToVault(message(image(link)), "self", on).isEmpty())
        val mixed = message(MessageContent.Album(listOf(attachment("a.jpg"), link)))
        assertTrue(AutoVaultRules.attachmentsToVault(mixed, "self", on).isEmpty())
    }

    @Test
    fun `an album is vaulted whole, each tile under its own cache id`() {
        val album = message(MessageContent.Album(listOf(attachment("a.jpg"), attachment("b.jpg"))))
        assertEquals(listOf("a.jpg", "b.jpg"), AutoVaultRules.attachmentsToVault(album, "self", on).map { it.filename })
        assertEquals("m1#0", AutoVaultRules.cacheId(album, 0))
        assertEquals("m1#1", AutoVaultRules.cacheId(album, 1))
        assertEquals("m1", AutoVaultRules.cacheId(message(image()), 0))
    }

    @Test
    fun `an album with one view-once tile is left alone entirely`() {
        // The message becomes a single marker, so it cannot be half moved.
        val album = message(MessageContent.Album(listOf(attachment("a.jpg"), attachment("b.jpg", viewOnce = true))))
        assertTrue(AutoVaultRules.attachmentsToVault(album, "self", on).isEmpty())
    }

    @Test
    fun `voice notes and files are vaulted, text and legacy links are not`() {
        val voice = message(MessageContent.Voice(url = "u", durationMs = 1, attachment = attachment("v.m4a")))
        val file =
            message(
                MessageContent.File(
                    url = "u",
                    fileName = "d.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 1,
                    attachment = attachment("d.pdf"),
                ),
            )
        assertEquals(1, AutoVaultRules.attachmentsToVault(voice, "self", on).size)
        assertEquals(1, AutoVaultRules.attachmentsToVault(file, "self", on).size)
        assertTrue(AutoVaultRules.attachmentsToVault(message(MessageContent.Text("hi")), "self", on).isEmpty())
        assertTrue(AutoVaultRules.attachmentsToVault(message(image(attachment = null)), "self", on).isEmpty())
    }
}
