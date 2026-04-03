package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based Kyber pre-key store for post-quantum key exchange.
 * Each Kyber pre-key is persisted as a binary file under
 * `signal/kyber_prekeys/{keyId}.bin`.
 *
 * Kyber pre-keys provide post-quantum forward secrecy as part of
 * the PQXDH protocol extension to X3DH.
 */
@Singleton
class SanchrKyberPreKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : KyberPreKeyStore {

    private val kyberPreKeyDir: File by lazy {
        File(context.filesDir, "signal/kyber_prekeys").also { it.mkdirs() }
    }

    private val cache = ConcurrentHashMap<Int, KyberPreKeyRecord>()

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

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeyFile(kyberPreKeyId).writeBytes(record.serialize())
        cache[kyberPreKeyId] = record
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return cache.containsKey(kyberPreKeyId) || kyberPreKeyFile(kyberPreKeyId).exists()
    }

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ECPublicKey?,
    ) {
        // In the one-time model, mark as used by removing it.
        // If using last-resort keys, keep it.
        // For now, we keep last-resort Kyber keys (do not remove).
    }

    /**
     * Wipes all Kyber pre-key data. Called on account deletion.
     */
    fun wipeAll() {
        cache.clear()
        kyberPreKeyDir.listFiles()?.forEach { it.delete() }
    }

    private fun kyberPreKeyFile(kyberPreKeyId: Int): File =
        File(kyberPreKeyDir, "$kyberPreKeyId.bin")
}
