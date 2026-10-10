package com.sanchr.feature.chats.shared

import com.sanchr.core.model.Message
import com.sanchr.core.model.MessageContent
import com.sanchr.feature.chats.MessageStatus
import com.sanchr.feature.chats.MessageUiModel

/**
 * The parts of a message the Media / Links / Docs browser needs.
 *
 * Deliberately not the transcript's mapper: that one resolves reply quotes,
 * reaction chips, delivery status and failure taxonomy against the loaded
 * batch, none of which a grid of thumbnails or a list of links reads. Status
 * is reported as delivered because these rows carry no ticks.
 */
internal fun List<Message>.toSharedContentModels(selfUserId: String): List<MessageUiModel> =
    map { message ->
        val album = message.content as? MessageContent.Album
        MessageUiModel(
            id = message.id,
            text = message.content.browserText(),
            timestamp = message.timestamp.toEpochMilliseconds(),
            isFromMe = message.senderId == selfUserId,
            status = MessageStatus.DELIVERED,
            contentType = message.content.browserContentType(),
            attachment = message.content.browserAttachment(),
            albumAttachments = album?.attachments.orEmpty(),
            isViewOnce = message.content.browserAttachment()?.isViewOnce == true,
        )
    }

/** The link text for a text message; a caption otherwise, which no row shows. */
private fun MessageContent.browserText(): String =
    when (this) {
        is MessageContent.Text -> body
        is MessageContent.Image -> caption.orEmpty()
        is MessageContent.Album -> caption.orEmpty()
        else -> ""
    }

/** The same tags the transcript uses, so [SharedContent.partition] reads one vocabulary. */
private fun MessageContent.browserContentType(): String =
    when (this) {
        is MessageContent.Text -> "text"
        is MessageContent.Image -> "image"
        is MessageContent.Album -> MessageContent.Album.CONTENT_TYPE
        is MessageContent.File -> "file"
        is MessageContent.Voice -> "voice"
        else -> "other"
    }

private fun MessageContent.browserAttachment() =
    when (this) {
        is MessageContent.Image -> attachment
        is MessageContent.Album -> first
        is MessageContent.File -> attachment
        is MessageContent.Voice -> attachment
        else -> null
    }
