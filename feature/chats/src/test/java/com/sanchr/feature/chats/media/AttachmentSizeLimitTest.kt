package com.sanchr.feature.chats.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The size limit on an outgoing attachment.
 *
 * Android had none. Preparing a file reads it whole and then costs about three
 * times its size in heap — the bytes, the ciphertext, and the copy handed to
 * the upload — so a long 4K clip was an unavoidable `OutOfMemoryError` on the
 * way to an upload the server would have refused anyway.
 */
class AttachmentSizeLimitTest {
    @Test
    fun `the limit is the server's own, and iOS's`() {
        // backend MAX_UPLOAD_SIZE = 104_857_600; iOS FileSource.maxBytes.
        assertEquals(104_857_600L, AttachmentPreparer.MAX_BYTES)
    }

    @Test
    fun `a file at the limit is allowed and one past it is not`() {
        assertTrue(AttachmentPreparer.isSizeAcceptable(AttachmentPreparer.MAX_BYTES))
        assertFalse(AttachmentPreparer.isSizeAcceptable(AttachmentPreparer.MAX_BYTES + 1))
    }

    @Test
    fun `an empty file is refused rather than uploaded as nothing`() {
        assertFalse(AttachmentPreparer.isSizeAcceptable(0))
        assertFalse(AttachmentPreparer.isSizeAcceptable(-1))
    }

    @Test
    fun `an ordinary photo and video are both well within it`() {
        assertTrue(AttachmentPreparer.isSizeAcceptable(3L * 1024 * 1024))
        assertTrue(AttachmentPreparer.isSizeAcceptable(40L * 1024 * 1024))
    }
}
