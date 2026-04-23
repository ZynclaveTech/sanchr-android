package com.sanchr.core.crypto.store

import com.sanchr.core.database.dao.SignalSessionDao
import com.sanchr.core.database.entity.SignalSessionEntity
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

/**
 * Room-backed session store. Session records are opaque libsignal blobs
 * persisted to the `signal_sessions` table, keyed by `"name.deviceId"`.
 *
 * Blocking DAO methods are used because libsignal's [SessionStore] interface
 * is synchronous; callers must invoke these from a safe single-threaded
 * dispatcher (`SignalDispatcher`).
 */
@Singleton
class SanchrSessionStore
    @Inject
    constructor(
        private val sessionDao: SignalSessionDao,
    ) : SessionStore {
        override fun loadSession(address: SignalProtocolAddress): SessionRecord {
            val entity = sessionDao.getBlocking(address.toRoomKey())
            // libsignal contract: if no session exists, return a fresh empty record.
            return entity?.sessionRecord?.let { SessionRecord(it) } ?: SessionRecord()
        }

        override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
            addresses.mapNotNull { address ->
                sessionDao.getBlocking(address.toRoomKey())?.sessionRecord?.let(::SessionRecord)
            }

        override fun getSubDeviceSessions(name: String): List<Int> {
            // LIKE prefix uses "name.%" — safe because '.' is not a SQL wildcard.
            val keys = sessionDao.listAddressesBlocking("$name.%")
            return keys.mapNotNull { key ->
                val device = key.substringAfterLast('.').toIntOrNull() ?: return@mapNotNull null
                // libsignal's contract excludes the primary device (deviceId=1).
                device.takeIf { it != 1 }
            }
        }

        override fun storeSession(
            address: SignalProtocolAddress,
            record: SessionRecord,
        ) {
            sessionDao.upsertBlocking(
                SignalSessionEntity(
                    address = address.toRoomKey(),
                    sessionRecord = record.serialize(),
                ),
            )
        }

        override fun containsSession(address: SignalProtocolAddress): Boolean = sessionDao.existsBlocking(address.toRoomKey())

        override fun deleteSession(address: SignalProtocolAddress) {
            sessionDao.deleteBlocking(address.toRoomKey())
        }

        override fun deleteAllSessions(name: String) {
            sessionDao.deleteByPrefixBlocking("$name.%")
        }

        /** Wipes all session data. Called on account deletion. */
        fun wipeAll() {
            sessionDao.deleteAllBlocking()
        }
    }
