package com.sanchr.core.crypto.store

import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * The first messages already accepted against a long-lived Kyber pre-key.
 *
 * A one-time pre-key is spent by the first message sent to it, which is what
 * stops that message being replayed: the second time, the key is gone. The
 * Kyber pre-key this app publishes is not one-time — it stays until it is
 * rotated — and the server hands out bundles with no one-time key at all once
 * a user's supply runs out. A first message sent to such a bundle has nothing
 * spent behind it. While the session it started is still held, the replay is
 * recognised against that session; once it is not, the only record that the
 * message was ever seen is the one kept here.
 *
 * Each first message has its own base key, so remembering which base keys
 * have been used with which pre-keys is enough. They are public values.
 *
 * Three things this is careful about, because each is a way for the record to
 * stop meaning what it says:
 *
 *  - An entry is kept for as long as the Kyber pre-key it belongs to is still
 *    held, and dropped only when that key is gone. Forgetting the oldest
 *    entries to make room would let anyone able to start sessions push a live
 *    entry out and then replay the message it stood for.
 *  - When a key's entries reach the limit, new first messages to it are
 *    refused. That is the point at which the key should have been rotated.
 *  - A record that exists but cannot be read is not treated as empty. It is
 *    read fresh on every use, and a failure to read or save it refuses the
 *    message.
 */
class SeenBaseKeys(
    private val file: File,
    private val limitPerKey: Int = DEFAULT_LIMIT_PER_KEY,
) {
    /** This pre-key has accepted as many first messages as will be remembered. */
    class FullException : IOException("the record of used base keys is full for this pre-key")

    /**
     * Records that [baseKey] was used with this pair of pre-keys.
     *
     * @param isHeld whether a Kyber pre-key with this id is still in the
     *   store. Entries for keys that are not are dropped here.
     * @return false if it already had been used, in which case nothing changes.
     * @throws IOException if the record cannot be read or saved, or is full
     *   for this key. A base key that is not durably remembered would be
     *   accepted again after a restart, so the message is refused rather than
     *   read on a promise that cannot be kept.
     */
    @Synchronized
    fun insert(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ByteArray,
        isHeld: (Int) -> Boolean,
    ): Boolean {
        val prefix = "$kyberPreKeyId:"
        val entry = "$prefix$signedPreKeyId:" + Base64.getEncoder().encodeToString(baseKey)
        val entries =
            load().filter { saved ->
                // The id being recorded is in use by definition.
                val id = saved.substringBefore(':').toIntOrNull() ?: return@filter false
                id == kyberPreKeyId || isHeld(id)
            }
        if (entry in entries) return false
        if (entries.count { it.startsWith(prefix) } >= limitPerKey) throw FullException()

        // Written beside the file and moved over it, so a crash part-way
        // leaves the old record rather than half of the new one.
        file.parentFile?.mkdirs()
        val pending = File(file.path + ".tmp")
        pending.writeText((entries + entry).joinToString(separator = "\n", postfix = "\n"))
        if (!pending.renameTo(file)) {
            pending.delete()
            throw IOException("could not save the record of used base keys")
        }
        return true
    }

    /** No file is an empty record. A file that cannot be read is an error, never an empty record. */
    private fun load(): List<String> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val DEFAULT_LIMIT_PER_KEY = 20_000
    }
}
