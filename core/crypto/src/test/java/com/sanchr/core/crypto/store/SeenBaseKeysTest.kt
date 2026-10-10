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
    private val held: (Int) -> Boolean = { true }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun key(byte: Int) = ByteArray(33) { byte.toByte() }

    private fun saved() = file.readLines().filter { it.isNotBlank() }

    @Test
    fun `a base key is accepted once for a pair of pre-keys`() {
        val seen = SeenBaseKeys(file)

        assertTrue(seen.insert(1, 2, key(7), held))
        assertFalse(seen.insert(1, 2, key(7), held))
        // A different message, or the same base key against other pre-keys, is not a reuse.
        assertTrue(seen.insert(1, 2, key(8), held))
        assertTrue(seen.insert(9, 2, key(7), held))
        assertTrue(seen.insert(1, 9, key(7), held))
    }

    @Test
    fun `it is remembered across a restart`() {
        // The case that matters: the session may be gone, and so is anything
        // kept only in memory.
        assertTrue(SeenBaseKeys(file).insert(1, 2, key(7), held))

        assertFalse(SeenBaseKeys(file).insert(1, 2, key(7), held))
    }

    @Test
    fun `what another instance recorded is seen and kept`() {
        val first = SeenBaseKeys(file)
        val second = SeenBaseKeys(file)

        assertTrue(first.insert(1, 2, key(1), held))
        assertTrue(second.insert(1, 2, key(2), held))

        assertFalse(first.insert(1, 2, key(2), held))
        assertFalse(second.insert(1, 2, key(1), held))
        assertEquals(2, saved().size)
    }

    @Test
    fun `a full key refuses new messages and forgets nothing`() {
        // Making room by forgetting the oldest would let anyone able to
        // start sessions push a live entry out and replay what it stood for.
        val seen = SeenBaseKeys(file, limitPerKey = 3)
        (1..3).forEach { assertTrue(seen.insert(1, 1, key(it), held)) }

        assertFailsWith<SeenBaseKeys.FullException> { seen.insert(1, 1, key(4), held) }
        (1..3).forEach { assertFalse(seen.insert(1, 1, key(it), held), "key $it") }
        // The limit is per key: another key still has room.
        assertTrue(seen.insert(2, 1, key(4), held))
    }

    @Test
    fun `entries go when the key they belong to does, and not before`() {
        val seen = SeenBaseKeys(file)
        assertTrue(seen.insert(1, 1, key(1), held))
        assertTrue(seen.insert(2, 1, key(2), held))

        // Key 1 has been rotated away; key 2 is still held.
        assertTrue(seen.insert(3, 1, key(3)) { it != 1 })

        assertEquals(listOf("2", "3"), saved().map { it.substringBefore(':') })
    }

    @Test
    fun `a message is refused when the record cannot be saved`() {
        // What cannot be durably remembered is not accepted.
        val blocker = File(directory, "not-a-directory").apply { writeText("x") }
        val seen = SeenBaseKeys(File(blocker, "seen.txt"))

        assertFailsWith<IOException> { seen.insert(1, 2, key(7), held) }
    }

    @Test
    fun `a record that cannot be read refuses rather than starting over`() {
        // Starting over would accept everything it had ever refused.
        file.mkdirs() // a directory where the record should be: it exists and cannot be read
        val seen = SeenBaseKeys(file)

        assertFailsWith<IOException> { seen.insert(1, 2, key(7), held) }
        assertTrue(file.isDirectory)
    }

    @Test
    fun `clearing forgets everything, as wiping the account must`() {
        val seen = SeenBaseKeys(file)
        seen.insert(1, 2, key(7), held)

        seen.clear()

        assertFalse(file.exists())
        assertTrue(seen.insert(1, 2, key(7), held))
    }
}
