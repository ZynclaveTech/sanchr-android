package com.sanchr.sync.vault

import com.sanchr.core.datastore.SessionManager
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.messaging.media.AttachmentDownloader
import com.sanchr.domain.messaging.vault.AutoVaultRules
import com.sanchr.domain.vault.VaultRepository
import com.sanchr.domain.vault.VaultThumbnails
import javax.inject.Inject

/**
 * Moves one received message's media into the vault and leaves a marker in
 * the chat, as iOS `routeIncomingMessageToVault`.
 *
 * Everything is decided again here, from what is stored now, rather than
 * trusted from when the work was scheduled: the switch may have been turned
 * off since, or the message deleted.
 *
 * Order matters. The media is only removed from the chat once every piece of
 * it is in the vault; if anything fails first, the message is left exactly as
 * it arrived. The decrypted copies in the cache go last, after the chat row
 * no longer points at them.
 */
class AutoVaultIncoming
    @Inject
    constructor(
        private val messageRepository: MessageRepository,
        private val sessionManager: SessionManager,
        private val attachmentDownloader: AttachmentDownloader,
        private val vaultRepository: VaultRepository,
    ) {
        /** True when the message was moved; false when there was nothing to move. */
        suspend operator fun invoke(messageId: String): Boolean {
            val message = messageRepository.message(messageId) ?: return false
            val policy = messageRepository.vaultPolicy(message.conversationId)
            val attachments = AutoVaultRules.attachmentsToVault(message, sessionManager.getUserId(), policy)
            if (attachments.isEmpty()) return false

            // Every download before any upload, so a file that is gone from
            // the server fails the whole message before the vault holds part
            // of it.
            val files =
                attachments.mapIndexed {
                        index,
                        attachment,
                    ->
                    attachmentDownloader.open(AutoVaultRules.cacheId(message, index), attachment)
                }
            attachments.forEachIndexed { index, attachment ->
                val bytes = files[index].readBytes()
                vaultRepository.createItem(
                    name = attachment.filename?.takeIf { it.isNotBlank() } ?: "vaulted-${message.id}-$index",
                    data = bytes,
                    mimeType = attachment.mimeType,
                    thumbnailJpeg = if (attachment.mimeType.startsWith("image/")) VaultThumbnails.forImage(bytes) else null,
                )
            }
            messageRepository.tombstoneAutoVaulted(message.id)
            attachments.forEachIndexed { index, attachment ->
                attachmentDownloader.evict(AutoVaultRules.cacheId(message, index), attachment.mimeType)
            }
            return true
        }
    }
