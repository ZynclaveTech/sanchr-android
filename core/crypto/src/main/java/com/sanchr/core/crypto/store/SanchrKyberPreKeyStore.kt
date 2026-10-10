package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore

/**
 * File-based Kyber pre-key store for post-quantum key exchange.
 * Each Kyber pre-key is persisted as a binary file under
 * `signal/kyber_prekeys/{keyId}.bin`.
 *
 * Kyber pre-keys provide post-quantum forward secrecy as part of
 * the PQXDH protocol extension to X3DH.
 */
@Singleton
class SanchrKyberPreKeyStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : KyberPreKeyStore {
        private val kyberPreKeyDir: File by lazy {
            File(context.filesDir, "signal/kyber_prekeys").also { it.mkdirs() }
        }

        private val cache = ConcurrentHashMap<Int, KyberPreKeyRecord>()

        /**
         * First messages already accepted against a Kyber pre-key. Beside the
         * key directory, not in it: every file in there is read as a key.
         */
        private val seenBaseKeys: SeenBaseKeys by lazy {
            SeenBaseKeys(File(context.filesDir, "signal/kyber_base_keys_seen.txt"))
        }

        override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
            cache[kyberPreKeyId]?.let { return it }

            val file = kyberPreKeyFile(kyberPreKeyId)
            if (!file.exists()) {
                throw InvalidKeyIdException("No Kyber pre-key found for ID: $kyberPreKeyId")
            }

            val record = KyberPreKeyRecord(file.readBytes())
            cache[kyberPreKeyId] = record
            return record
        }

        override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
            val files = kyberPreKeyDir.listFiles() ?: return emptyList()
            return files.mapNotNull { file ->
                val keyId = file.nameWithoutExtension.toIntOrNull() ?: return@mapNotNull null
                cache.getOrPut(keyId) { KyberPreKeyRecord(file.readBytes()) }
            }
        }

        override fun storeKyberPreKey(
            kyberPreKeyId: Int,
            record: KyberPreKeyRecord,
        ) {
            kyberPreKeyFile(kyberPreKeyId).writeBytes(record.serialize())
            cache[kyberPreKeyId] = record
        }

        override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
            cache.containsKey(kyberPreKeyId) || kyberPreKeyFile(kyberPreKeyId).exists()

        /**
         * Forgets a key that has been replaced and is past its grace period.
         * The base keys recorded against it go with it the next time that
         * record is written; see [SeenBaseKeys].
         */
        fun removeKyberPreKey(kyberPreKeyId: Int) {
            cache.remove(kyberPreKeyId)
            kyberPreKeyFile(kyberPreKeyId).delete()
        }

        /**
         * The pair of keys a rotation has stored and not yet confirmed
         * published — signed pre-key id, then Kyber pre-key id — so a retry
         * sends the same pair instead of making another.
         */
        var pendingRotation: Pair<Int, Int>?
            get() =
                runCatching {
                    pendingRotationFile
                        .readText()
                        .trim()
                        .split(':')
                        .map(String::toInt)
                }.getOrNull()
                    ?.takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] }
            set(value) {
                if (value == null) {
                    pendingRotationFile.delete()
                } else {
                    pendingRotationFile.parentFile?.mkdirs()
                    pendingRotationFile.writeText("${value.first}:${value.second}")
                }
            }

        /** Beside the key directory, not in it: every file in there is read as a key. */
        private val pendingRotationFile: File by lazy { File(context.filesDir, "signal/prekey_rotation_pending") }

        /**
         * Refuses a base key this key has already been used with.
         *
         * The Kyber pre-key itself stays until rotation replaces it; what is
         * spent is the one base key. This did nothing at all, so a first
         * message sent without a one-time pre-key could be replayed as new
         * once the session it started was no longer held.
         */
        override fun markKyberPreKeyUsed(
            kyberPreKeyId: Int,
            signedPreKeyId: Int,
            baseKey: ECPublicKey?,
        ) {
            // Nothing to tell one message from another by.
            val key = baseKey?.serialize() ?: return
            if (!seenBaseKeys.insert(kyberPreKeyId, signedPreKeyId, key, ::containsKyberPreKey)) {
                throw ReusedBaseKeyException()
            }
        }

        /**
         * Wipes all Kyber pre-key data. Called on account deletion.
         */
        fun wipeAll() {
            cache.clear()
            kyberPreKeyDir.listFiles()?.forEach { it.delete() }
            seenBaseKeys.clear()
            pendingRotation = null
        }

        private fun kyberPreKeyFile(kyberPreKeyId: Int): File = File(kyberPreKeyDir, "$kyberPreKeyId.bin")
    }
