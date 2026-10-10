package com.sanchr.feature.chats.info

import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.domain.messaging.media.AttachmentUploader

/**
 * What a conversation's [ChatVaultPolicy] means for a send and for the viewer.
 *
 * Kept apart from the view models so the rules have one statement and can be
 * tested without a view model around them.
 */
object VaultPolicyRules {
    /**
     * Whether view-once can apply to a file of [mimeType].
     *
     * Photos and videos only. iOS stamps every attachment, but the view-once
     * viewer here opens those two kinds; a voice note or a PDF marked
     * view-once would arrive on an Android phone as something that cannot be
     * opened at all, which is worse than arriving as an ordinary attachment.
     */
    fun viewOnceApplies(mimeType: String): Boolean = mimeType.startsWith("image/") || mimeType.startsWith("video/")

    /** [prepared] as this chat's policy says it should be sent. */
    fun outgoing(
        prepared: AttachmentUploader.Prepared,
        policy: ChatVaultPolicy?,
    ): AttachmentUploader.Prepared =
        if (policy?.viewOnceOutgoing == true && viewOnceApplies(prepared.mimeType)) prepared.asViewOnce() else prepared

    /**
     * Whether a set has to go as separate view-once messages rather than one
     * album. An album is a single message that stays in the chat; nothing
     * about it can be opened once and then be gone.
     */
    fun sendsSeparately(
        prepared: List<AttachmentUploader.Prepared>,
        policy: ChatVaultPolicy?,
    ): Boolean = policy?.viewOnceOutgoing == true && prepared.any { viewOnceApplies(it.mimeType) }

    /**
     * Whether media from this chat is shielded from capture while open.
     *
     * Either switch is enough: the account-wide one covers every chat, and
     * the chat's own covers this one even when the account-wide one is off.
     * Neither can turn the other off.
     */
    fun captureProtected(
        accountWide: Boolean,
        policy: ChatVaultPolicy?,
    ): Boolean = accountWide || policy?.captureProtection == true
}
