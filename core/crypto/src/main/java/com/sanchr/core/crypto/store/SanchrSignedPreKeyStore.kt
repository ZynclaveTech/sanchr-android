package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * File-based signed pre-key store. Each signed pre-key is persisted as a binary
 * file under `signal/signed_prekeys/{keyId}.bin`.
 *
 * Signed pre-keys are medium-term Curve25519 key pairs signed by the identity key.
 * They are rotated approximately every 30 days.
 */
@Singleton
class SanchrSignedPreKeyStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SignedPreKeyStore {
        private val signedPreKeyDir: File by lazy {
            File(context.filesDir, "signal/signed_prekeys").also { it.mkdirs() }
        }

        private val cache = ConcurrentHashMap<Int, SignedPreKeyRecord>()

        override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
            cache[signedPreKeyId]?.let { return it }

            val file = signedPreKeyFile(signedPreKeyId)
            if (!file.exists()) {
                throw InvalidKeyIdException("No signed pre-key found for ID: $signedPreKeyId")
            }

            val record = SignedPreKeyRecord(file.readBytes())
            cache[signedPreKeyId] = record
            return record
        }

        override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
            val files = signedPreKeyDir.listFiles() ?: return emptyList()
            return files.mapNotNull { file ->
                val keyId = file.nameWithoutExtension.toIntOrNull() ?: return@mapNotNull null
                cache.getOrPut(keyId) { SignedPreKeyRecord(file.readBytes()) }
            }
        }

        override fun storeSignedPreKey(
            signedPreKeyId: Int,
            record: SignedPreKeyRecord,
        ) {
            signedPreKeyFile(signedPreKeyId).writeBytes(record.serialize())
            cache[signedPreKeyId] = record
        }

        override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
            cache.containsKey(signedPreKeyId) || signedPreKeyFile(signedPreKeyId).exists()

        override fun removeSignedPreKey(signedPreKeyId: Int) {
            cache.remove(signedPreKeyId)
            signedPreKeyFile(signedPreKeyId).delete()
        }

        /**
         * Returns the next available signed pre-key ID.
         */
        fun getNextSignedPreKeyId(): Int {
            val existingIds =
                signedPreKeyDir
                    .listFiles()
                    ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                    ?: emptyList()
            return if (existingIds.isEmpty()) 1 else existingIds.max() + 1
        }

        /**
         * Wipes all signed pre-key data. Called on account deletion.
         */
        fun wipeAll() {
            cache.clear()
            signedPreKeyDir.listFiles()?.forEach { it.delete() }
        }

        private fun signedPreKeyFile(signedPreKeyId: Int): File = File(signedPreKeyDir, "$signedPreKeyId.bin")
    }
