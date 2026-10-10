package com.sanchr.domain.messaging.media

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A message id is someone else's string; it must not be able to choose where a file goes. */
class AttachmentCacheNamesTest {
    @Test
    fun `the ids this app and iOS produce keep their names`() {
        // Unchanged, so files cached before this existed are still found.
        val uuid = "3F2504E0-4F89-41D3-9A0C-0305E82C3301"
        assertEquals(uuid, AttachmentCacheNames.fileName(uuid))
        assertEquals("$uuid#3", AttachmentCacheNames.fileName("$uuid#3"))
        assertEquals("msg_1-a", AttachmentCacheNames.fileName("msg_1-a"))
    }

    @Test
    fun `an id that could leave the cache directory cannot`() {
        val hostile = listOf("../../databases/sanchr-database", "/etc/hosts", "a/b", "..", ".", "a\\b", "a\u0000b", "x".repeat(500), "")
        val cache = File("/data/cache/attachments")
        hostile.forEach { id ->
            val name = AttachmentCacheNames.fileName(id)
            assertTrue(name.matches(Regex("[0-9a-f]{64}")), "not hashed: $id")
            assertEquals(cache.canonicalPath, File(cache, "$name.jpg").canonicalFile.parent, id)
        }
    }

    @Test
    fun `different hostile ids do not share a file`() {
        assertNotEquals(AttachmentCacheNames.fileName("../a"), AttachmentCacheNames.fileName("../b"))
    }

    @Test
    fun `only the plain shape is a well-formed id`() {
        assertTrue(AttachmentCacheNames.isWellFormedId("3F2504E0-4F89-41D3-9A0C-0305E82C3301"))
        assertFalse(AttachmentCacheNames.isWellFormedId("../x"))
        assertFalse(AttachmentCacheNames.isWellFormedId(""))
        // `#n` belongs to this app's own tile keys, never to an id off the wire.
        assertFalse(AttachmentCacheNames.isWellFormedId("m1#0"))
    }
}
