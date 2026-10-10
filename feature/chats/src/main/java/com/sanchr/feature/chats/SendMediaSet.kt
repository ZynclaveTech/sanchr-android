package com.sanchr.feature.chats

import com.sanchr.core.common.Result
import com.sanchr.core.model.ChatVaultPolicy
import com.sanchr.domain.messaging.SendAlbumUseCase
import com.sanchr.domain.messaging.SendAttachmentUseCase
import com.sanchr.domain.messaging.media.AttachmentUploader
import com.sanchr.feature.chats.info.VaultPolicyRules

/**
 * Sends a reviewed set of files the way the conversation's [policy] says.
 *
 * Ordinarily a set is one album, as iOS sends it — one bubble and one
 * notification rather than ten. In a chat whose media goes view-once it is one
 * message per file instead: an album is a single message that stays in the
 * chat, and cannot be opened once and then be gone.
 */
internal class SendMediaSet(
    private val sendOne: SendAttachmentUseCase,
    private val sendAlbum: SendAlbumUseCase,
) {
    suspend operator fun invoke(
        conversationId: String,
        prepared: List<AttachmentUploader.Prepared>,
        policy: ChatVaultPolicy?,
        replyToId: String?,
        onProgress: (Float) -> Unit,
    ): Result<*> =
        when {
            VaultPolicyRules.sendsSeparately(prepared, policy) ->
                separately(conversationId, prepared.map { VaultPolicyRules.outgoing(it, policy) }, replyToId, onProgress)
            prepared.size > 1 -> sendAlbum(conversationId, prepared, replyToId, onProgress)
            else -> sendOne(conversationId, prepared.first(), replyToId, onProgress)
        }

    /**
     * Stops at the first failure, so a partial send is reported as one rather
     * than as success. The reply, if any, belongs to the first message.
     */
    private suspend fun separately(
        conversationId: String,
        prepared: List<AttachmentUploader.Prepared>,
        replyToId: String?,
        onProgress: (Float) -> Unit,
    ): Result<*> {
        prepared.forEachIndexed { index, item ->
            val result =
                sendOne(conversationId, item, replyToId.takeIf { index == 0 }) { fraction ->
                    onProgress((index + fraction) / prepared.size)
                }
            if (result is Result.Error) return result
        }
        return Result.Success(Unit)
    }
}
