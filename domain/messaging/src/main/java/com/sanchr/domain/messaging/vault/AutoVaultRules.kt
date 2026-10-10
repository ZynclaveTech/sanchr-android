package com.sanchr.domain.messaging.vault

import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent

/**
 * Which of a received message's attachments Auto-Vault Incoming moves into
 * the vault.
 *
 * The vault keeps things. That makes two kinds of media off limits however
 * the switch is set, for the reason `MediaAutoSavePolicy` keeps them out of
 * the gallery: a view-once photo was shown once on purpose, and a
 * disappearing message carries a timer both people can see. Vaulting either
 * would quietly turn "gone after this" into "kept", on the recipient's say-so
 * alone.
 */
object AutoVaultRules {
    fun attachmentsToVault(
        message: Message,
        selfUserId: String?,
        policy: ChatVaultPolicy,
    ): List<MediaAttachment> {
        if (!policy.autoVaultIncoming) return emptyList()
        // Incoming only; what this device sent is already the user's own.
        if (message.senderId == selfUserId) return emptyList()
        if (message.expiresAt != null) return emptyList()
        val attachments = attachmentsOf(message.content)
        // Uploaded media only. An attachment may instead carry a plain URL of
        // the sender's choosing, and vaulting fetches without anyone tapping
        // anything: that would be a request to wherever a message said, made
        // the moment it arrived.
        if (attachments.any { it.mediaId == null }) return emptyList()
        // All or nothing: the message becomes a single marker in the chat,
        // so it cannot be half moved.
        return if (attachments.any { it.isViewOnce == true }) emptyList() else attachments
    }

    /**
     * The id an attachment is cached under, which for an album is per tile.
     * Has to agree with the chat screen's own keys, or vaulting would
     * download a second copy and leave the first one behind in the cache.
     */
    fun cacheId(
        message: Message,
        index: Int,
    ): String = if (message.content is MessageContent.Album) message.id + "#" + index else message.id

    private fun attachmentsOf(content: MessageContent): List<MediaAttachment> =
        when (content) {
            is MessageContent.Image -> listOfNotNull(content.attachment)
            is MessageContent.Album -> content.attachments
            is MessageContent.Voice -> listOfNotNull(content.attachment)
            is MessageContent.File -> listOfNotNull(content.attachment)
            is MessageContent.Text, is MessageContent.Location, is MessageContent.Contact, is MessageContent.System -> emptyList()
        }
}
