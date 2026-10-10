package com.sanchr.feature.chats.info

import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.domain.messaging.media.AttachmentUploader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a chat's vault policy does to a send and to the viewer. */
class VaultPolicyRulesTest {
    private fun prepared(mimeType: String) = AttachmentUploader.Prepared(bytes = byteArrayOf(1), mimeType = mimeType, fileName = "f")

    private val viewOnce = ChatVaultPolicy(viewOnceOutgoing = true)

    @Test
    fun `a chat with no policy sends media as it always did`() {
        assertNull(VaultPolicyRules.outgoing(prepared("image/jpeg"), null).isViewOnce)
        assertNull(VaultPolicyRules.outgoing(prepared("image/jpeg"), ChatVaultPolicy.DEFAULT).isViewOnce)
    }

    @Test
    fun `view once marks the photos and videos sent from the chat`() {
        assertEquals(true, VaultPolicyRules.outgoing(prepared("image/jpeg"), viewOnce).isViewOnce)
        assertEquals(true, VaultPolicyRules.outgoing(prepared("video/mp4"), viewOnce).isViewOnce)
    }

    @Test
    fun `view once leaves alone what the viewer could not open once`() {
        // A voice note or a PDF marked view-once would reach an Android phone
        // as something that cannot be opened at all.
        assertNull(VaultPolicyRules.outgoing(prepared("audio/mp4"), viewOnce).isViewOnce)
        assertNull(VaultPolicyRules.outgoing(prepared("application/pdf"), viewOnce).isViewOnce)
    }

    @Test
    fun `the other two switches do not make a send view-once`() {
        val others = ChatVaultPolicy(autoVaultIncoming = true, captureProtection = true)
        assertNull(VaultPolicyRules.outgoing(prepared("image/jpeg"), others).isViewOnce)
    }

    @Test
    fun `a set goes as separate messages only when view once would apply to it`() {
        val photos = listOf(prepared("image/jpeg"), prepared("image/png"))
        assertTrue(VaultPolicyRules.sendsSeparately(photos, viewOnce))
        assertFalse(VaultPolicyRules.sendsSeparately(photos, ChatVaultPolicy.DEFAULT))
        assertFalse(VaultPolicyRules.sendsSeparately(photos, null))
        assertFalse(VaultPolicyRules.sendsSeparately(listOf(prepared("application/pdf")), viewOnce))
    }

    @Test
    fun `either capture switch protects and neither turns the other off`() {
        val chatOn = ChatVaultPolicy(captureProtection = true)
        assertTrue(VaultPolicyRules.captureProtected(accountWide = true, policy = null))
        assertTrue(VaultPolicyRules.captureProtected(accountWide = true, policy = ChatVaultPolicy.DEFAULT))
        assertTrue(VaultPolicyRules.captureProtected(accountWide = false, policy = chatOn))
        assertFalse(VaultPolicyRules.captureProtected(accountWide = false, policy = ChatVaultPolicy.DEFAULT))
        assertFalse(VaultPolicyRules.captureProtected(accountWide = false, policy = null))
    }
}
