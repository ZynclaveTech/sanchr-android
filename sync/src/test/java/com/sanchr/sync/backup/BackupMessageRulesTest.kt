package com.sanchr.sync.backup

import com.sanchr.core.model.MediaAttachment
import com.sanchr.core.model.MediaContentEnvelope
import com.sanchr.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A backup keeps things. Some messages were sent on the understanding that nothing would. */
class BackupMessageRulesTest {
    private fun photo(viewOnce: Boolean?) =
        MediaAttachment(url = "sanchr-media://m", mimeType = "image/jpeg", sizeBytes = 10, isViewOnce = viewOnce)

    private fun body(vararg attachments: MediaAttachment) = MediaContentEnvelope.encode(MediaKind.IMAGE, attachments.toList())

    private fun kept(
        contentType: String = "text",
        contentBody: String = "hello",
        expiresAtMs: Long? = null,
        isDeleted: Boolean = false,
    ) = BackupMessageRules.belongsInBackup(contentType, contentBody, expiresAtMs, isDeleted)

    @Test
    fun `an ordinary message and an ordinary photo are backed up`() {
        assertTrue(kept())
        assertTrue(kept(contentType = "image", contentBody = body(photo(viewOnce = null))))
        assertTrue(kept(contentType = "image", contentBody = body(photo(viewOnce = false))))
    }

    /** Backed up, it outlives its timer by as long as the backup does. */
    @Test
    fun `a disappearing message is not, whether or not its time is up`() {
        assertFalse(kept(expiresAtMs = 1))
        assertFalse(kept(expiresAtMs = Long.MAX_VALUE))
        assertFalse(kept(contentType = "image", contentBody = body(photo(viewOnce = null)), expiresAtMs = Long.MAX_VALUE))
    }

    /** The backup would hold the keys to see it again. */
    @Test
    fun `view-once media is not`() {
        assertFalse(kept(contentType = "image", contentBody = body(photo(viewOnce = true))))
        assertFalse(kept(contentType = "album", contentBody = body(photo(viewOnce = null), photo(viewOnce = true))))
    }

    @Test
    fun `the marker left where a view-once was opened is backed up`() {
        assertTrue(kept(contentType = "system", contentBody = "viewOnceConsumed"))
    }

    @Test
    fun `a deleted message's empty placeholder is not`() {
        assertFalse(kept(contentBody = "", isDeleted = true))
    }

    @Test
    fun `text that merely looks like media is still text`() {
        assertTrue(kept(contentBody = """{"image":"not an attachment"}"""))
        assertTrue(kept(contentBody = "isViewOnce"))
    }

    /** An older backup, or one from an app without this rule, may hold all three. */
    @Test
    fun `the same rule decides what a restore takes`() {
        fun frame(
            contentType: String = "text",
            contentBody: String = "hello",
            expiresAtMs: Long? = null,
            isDeleted: Boolean = false,
        ) = BackupArchiveMessageFrame(
            id = "m1",
            conversationID = "c1",
            senderID = "peer",
            timestampMs = 1,
            contentType = contentType,
            contentBody = contentBody,
            status = "DELIVERED",
            expiresAtMs = expiresAtMs,
            isDeleted = isDeleted,
        )

        assertTrue(BackupMessageRules.belongsInBackup(frame()))
        assertFalse(BackupMessageRules.belongsInBackup(frame(expiresAtMs = 5)))
        assertFalse(BackupMessageRules.belongsInBackup(frame(isDeleted = true)))
        assertFalse(BackupMessageRules.belongsInBackup(frame(contentType = "image", contentBody = body(photo(viewOnce = true)))))
    }
}
