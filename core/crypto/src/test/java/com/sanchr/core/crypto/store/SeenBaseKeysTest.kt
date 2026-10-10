package com.sanchr.core.crypto.store

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The record that stops a first message sent without a one-time pre-key from being accepted twice. */
class SeenBaseKeysTest {
    private val directory: File = createTempDirectory("seen").toFile()
    private val file = File(directory, "seen.txt")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun key(byte: Int) = ByteArray(33) { byte.toByte() }

    @Test
    fun `a base key is accepted once for a pair of pre-keys`() {
        val seen = SeenBaseKeys(file)

        assertTrue(seen.insert(1, 2, key(7)))
        assertFalse(seen.insert(1, 2, key(7)))
        // A different message, or the same base key against other pre-keys, is not a reuse.
        assertTrue(seen.insert(1, 2, key(8)))
        assertTrue(seen.insert(9, 2, key(7)))
        assertTrue(seen.insert(1, 9, key(7)))
    }

    @Test
    fun `it is remembered across a restart`() {
        // The case that matters: the session may be gone, and so is anything
        // kept only in memory.
        assertTrue(SeenBaseKeys(file).insert(1, 2, key(7)))

        assertFalse(SeenBaseKeys(file).insert(1, 2, key(7)))
    }

    @Test
    fun `it does not grow without limit and forgets the oldest first`() {
        val seen = SeenBaseKeys(file, limit = 3)
        (1..4).forEach { assertTrue(seen.insert(1, 1, key(it))) }

        assertEquals(3, file.readLines().count { it.isNotBlank() })
        // The newest three are still refused; only the oldest was let go.
        (2..4).forEach { assertFalse(seen.insert(1, 1, key(it)), "key $it") }
    }

    @Test
    fun `a message is refused when the record cannot be saved`() {
        // What cannot be durably remembered is not accepted.
        val blocker = File(directory, "not-a-directory").apply { writeText("x") }
        val seen = SeenBaseKeys(File(blocker, "seen.txt"))

        assertFailsWith<IOException> { seen.insert(1, 2, key(7)) }
    }

    @Test
    fun `clearing forgets everything, as wiping the account must`() {
        val seen = SeenBaseKeys(file)
        seen.insert(1, 2, key(7))

        seen.clear()

        assertFalse(file.exists())
        assertTrue(seen.insert(1, 2, key(7)))
    }
}
