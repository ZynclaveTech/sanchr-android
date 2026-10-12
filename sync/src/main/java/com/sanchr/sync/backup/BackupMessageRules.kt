package com.sanchr.sync.backup

import com.sanchr.core.model.MediaContentEnvelope
import com.sanchr.core.model.MediaKind

/**
 * Which messages a backup may hold.
 *
 * A backup keeps things, somewhere else, for as long as the backup exists.
 * That makes three kinds of message wrong to put in one, however the backup
 * is protected:
 *
 * - a **disappearing** message, which both people were shown a timer for.
 *   Backed up, it outlives the timer by as long as the backup does.
 * - **view-once** media, which was sent to be seen once. The backup would
 *   hold the keys to see it again.
 * - a **deleted** message, of which only an empty placeholder remains.
 *
 * The same rule is applied on the way back in: a backup made before this
 * existed, or by an app that does not apply it, may hold all three.
 */
object BackupMessageRules {
    fun belongsInBackup(
        contentType: String,
        contentBody: String,
        expiresAtMs: Long?,
        isDeleted: Boolean,
    ): Boolean = !isDeleted && expiresAtMs == null && !isViewOnce(contentType, contentBody)

    fun belongsInBackup(frame: BackupArchiveMessageFrame): Boolean =
        belongsInBackup(frame.contentType, frame.contentBody, frame.expiresAtMs, frame.isDeleted)

    private fun isViewOnce(
        contentType: String,
        contentBody: String,
    ): Boolean =
        MediaContentEnvelope
            .decode(contentBody, MediaKind.fromWire(contentType))
            ?.attachments
            ?.any { it.isViewOnce == true } == true
}
