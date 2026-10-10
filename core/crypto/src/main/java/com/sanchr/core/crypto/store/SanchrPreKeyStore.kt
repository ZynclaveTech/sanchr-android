package com.sanchr.core.crypto.store

import com.sanchr.core.database.dao.SignalPreKeyDao
import com.sanchr.core.database.entity.SignalPreKeyEntity
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore

/**
 * Room-backed one-time pre-key store. Pre-key records are opaque libsignal
 * blobs persisted to the `signal_prekeys` table, keyed by `prekey_id`.
 */
@Singleton
class SanchrPreKeyStore
    @Inject
    constructor(
        private val preKeyDao: SignalPreKeyDao,
    ) : PreKeyStore {
        override fun loadPreKey(preKeyId: Int): PreKeyRecord {
            val entity =
                preKeyDao.getBlocking(preKeyId)
                    ?: throw InvalidKeyIdException("No pre-key found for ID: $preKeyId")
            return PreKeyRecord(entity.record)
        }

        override fun storePreKey(
            preKeyId: Int,
            record: PreKeyRecord,
        ) {
            preKeyDao.upsertBlocking(
                SignalPreKeyEntity(prekeyId = preKeyId, record = record.serialize()),
            )
        }

        override fun containsPreKey(preKeyId: Int): Boolean = preKeyDao.existsBlocking(preKeyId)

        override fun removePreKey(preKeyId: Int) {
            preKeyDao.deleteBlocking(preKeyId)
        }

        /**
         * Returns the next available pre-key ID (max existing + 1, or 1 if empty).
         * Used by [SignalKeyManager] when generating a fresh batch.
         */
        fun getNextPreKeyId(): Int = (preKeyDao.maxIdBlocking() ?: 0) + 1

        /**
         * One-time pre-keys still held on this device, i.e. those the server
         * has not yet handed out and had consumed.
         *
         * The Encryption Keys screen printed "-- keys remaining on server"; the
         * count this device holds is the number it can actually know, and it
         * falls as the server issues them.
         */
        suspend fun remainingPreKeyCount(): Int = preKeyDao.count()

        /** Wipes all pre-key data. Called on account deletion. */
        fun wipeAll() {
            preKeyDao.deleteAllBlocking()
        }
    }
