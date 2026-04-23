package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore

/**
 * File-based pre-key store. Each pre-key is persisted as a binary file under
 * the app's internal storage at `signal/prekeys/{keyId}.bin`.
 *
 * Pre-keys are one-time-use Curve25519 key pairs uploaded to the server and
 * consumed when a new session is established via X3DH.
 */
@Singleton
class SanchrPreKeyStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PreKeyStore {
        private val preKeyDir: File by lazy {
            File(context.filesDir, "signal/prekeys").also { it.mkdirs() }
        }

        /** In-memory cache to avoid repeated disk I/O for hot keys. */
        private val cache = ConcurrentHashMap<Int, PreKeyRecord>()

        override fun loadPreKey(preKeyId: Int): PreKeyRecord {
            cache[preKeyId]?.let { return it }

            val file = preKeyFile(preKeyId)
            if (!file.exists()) {
                throw InvalidKeyIdException("No pre-key found for ID: $preKeyId")
            }

            val record = PreKeyRecord(file.readBytes())
            cache[preKeyId] = record
            return record
        }

        override fun storePreKey(
            preKeyId: Int,
            record: PreKeyRecord,
        ) {
            preKeyFile(preKeyId).writeBytes(record.serialize())
            cache[preKeyId] = record
        }

        override fun containsPreKey(preKeyId: Int): Boolean = cache.containsKey(preKeyId) || preKeyFile(preKeyId).exists()

        override fun removePreKey(preKeyId: Int) {
            cache.remove(preKeyId)
            preKeyFile(preKeyId).delete()
        }

        /**
         * Returns the next available pre-key ID by scanning existing files.
         * The caller should use this as `startId` when generating a new batch.
         */
        fun getNextPreKeyId(): Int {
            val existingIds =
                preKeyDir
                    .listFiles()
                    ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                    ?: emptyList()
            return if (existingIds.isEmpty()) 1 else existingIds.max() + 1
        }

        /**
         * Wipes all pre-key data. Called on account deletion.
         */
        fun wipeAll() {
            cache.clear()
            preKeyDir.listFiles()?.forEach { it.delete() }
        }

        private fun preKeyFile(preKeyId: Int): File = File(preKeyDir, "$preKeyId.bin")
    }
