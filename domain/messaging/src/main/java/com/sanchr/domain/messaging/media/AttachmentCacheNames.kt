package com.sanchr.domain.messaging.media

import java.security.MessageDigest

/**
 * The file name an attachment is cached under.
 *
 * The id it is built from comes out of the message, and the message comes
 * from someone else: a payload may carry any string as its `message_id`. Used
 * as a file name as it stood, an id of `../../databases/x` named a file
 * outside the cache directory, which the downloader would then write
 * decrypted bytes to and, on eviction, delete.
 *
 * An id of the shape this app and iOS actually produce — letters, digits,
 * `-` and `_`, optionally `#n` for an album tile — is kept as it is, so files
 * cached before this existed are still found and still evicted. Anything else
 * is replaced by its hash: still a stable name for that message, and one with
 * no separators in it.
 */
object AttachmentCacheNames {
    private val SAFE = Regex("""^[A-Za-z0-9_-]{1,96}(#[0-9]{1,4})?$""")

    fun fileName(messageId: String): String = if (SAFE.matches(messageId)) messageId else sha256(messageId)

    /** Whether [id] is a shape a message or conversation id is allowed to have. */
    fun isWellFormedId(id: String): Boolean = SAFE.matches(id) && !id.contains('#')

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
