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
 * Bounded: the oldest are forgotten first. A base key that old was used with
 * pre-keys that have almost certainly been rotated since, and an unbounded
 * file that anyone can add to by sending a message is its own problem.
 */
class SeenBaseKeys(
    private val file: File,
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val entries: LinkedHashSet<String> by lazy {
        val saved = runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
        LinkedHashSet(saved.takeLast(limit))
    }

    /**
     * Records that [baseKey] was used with this pair of pre-keys.
     *
     * @return false if it already had been, in which case nothing changes.
     * @throws IOException if the record cannot be saved. A base key that is
     *   not durably remembered would be accepted again after a restart, so
     *   the message is refused rather than read on a promise that cannot be
     *   kept.
     */
    @Synchronized
    fun insert(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ByteArray,
    ): Boolean {
        val entry = "$kyberPreKeyId:$signedPreKeyId:" + Base64.getEncoder().encodeToString(baseKey)
        if (entry in entries) return false

        val updated = (entries + entry).toList().takeLast(limit)
        // Written beside the file and moved over it, so a crash part-way
        // leaves the old record rather than half of the new one.
        file.parentFile?.mkdirs()
        val pending = File(file.path + ".tmp")
        pending.writeText(updated.joinToString(separator = "\n", postfix = "\n"))
        if (!pending.renameTo(file)) {
            pending.delete()
            throw IOException("could not save the record of used base keys")
        }

        entries.clear()
        entries.addAll(updated)
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
        file.delete()
    }

    private companion object {
        const val DEFAULT_LIMIT = 4096
    }
}
