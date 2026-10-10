package com.sanchr.core.crypto.store

import com.sanchr.core.database.dao.SignalSignedPreKeyDao
import com.sanchr.core.database.entity.SignalSignedPreKeyEntity
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/** The signed pre-key this device is currently publishing. */
data class SignedPreKeyInfo(
    val id: Int,
    val createdAtMillis: Long,
)

/**
 * Room-backed signed pre-key store. Records are opaque libsignal blobs
 * persisted to the `signal_signed_prekeys` table.
 */
@Singleton
class SanchrSignedPreKeyStore
    @Inject
    constructor(
        private val signedPreKeyDao: SignalSignedPreKeyDao,
    ) : SignedPreKeyStore {
        override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
            val entity =
                signedPreKeyDao.getBlocking(signedPreKeyId)
                    ?: throw InvalidKeyIdException("No signed pre-key found for ID: $signedPreKeyId")
            return SignedPreKeyRecord(entity.record)
        }

        override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = signedPreKeyDao.getAllBlocking().map { SignedPreKeyRecord(it.record) }

        override fun storeSignedPreKey(
            signedPreKeyId: Int,
            record: SignedPreKeyRecord,
        ) {
            signedPreKeyDao.upsertBlocking(
                SignalSignedPreKeyEntity(
                    prekeyId = signedPreKeyId,
                    record = record.serialize(),
                    createdAt = record.timestamp,
                ),
            )
        }

        override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeyDao.existsBlocking(signedPreKeyId)

        override fun removeSignedPreKey(signedPreKeyId: Int) {
            signedPreKeyDao.deleteBlocking(signedPreKeyId)
        }

        /**
         * The newest signed pre-key held, or null when none has been
         * generated yet.
         *
         * Read by the Encryption Keys screen, which used to print
         * "Key ID: SPK-0001" and "Generated: --" as literals beside an
         * unconditional green "Active" — fabricated security state, on the one
         * screen whose whole purpose is to report the real thing.
         */
        fun newestSignedPreKey(): SignedPreKeyInfo? =
            signedPreKeyDao
                .getAllBlocking()
                .maxByOrNull { it.prekeyId }
                ?.let { SignedPreKeyInfo(id = it.prekeyId, createdAtMillis = it.createdAt) }

        /** Returns the next available signed pre-key ID (max + 1, or 1 if empty). */
        fun getNextSignedPreKeyId(): Int = (signedPreKeyDao.maxIdBlocking() ?: 0) + 1

        /** Wipes all signed pre-key data. Called on account deletion. */
        fun wipeAll() {
            signedPreKeyDao.deleteAllBlocking()
        }
    }
