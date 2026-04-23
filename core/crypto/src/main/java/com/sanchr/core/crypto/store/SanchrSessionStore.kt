package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

/**
 * File-based session store. Each session is persisted as a binary file under
 * `signal/sessions/{userId}_{deviceId}.bin`.
 *
 * Sessions contain the Double Ratchet state for an established E2EE channel
 * with a specific device of a specific user.
 */
@Singleton
class SanchrSessionStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SessionStore {
        private val sessionDir: File by lazy {
            File(context.filesDir, "signal/sessions").also { it.mkdirs() }
        }

        private val cache = ConcurrentHashMap<String, SessionRecord>()

        override fun loadSession(address: SignalProtocolAddress): SessionRecord {
            val key = addressKey(address)
            cache[key]?.let { return it }

            val file = sessionFile(address)
            if (!file.exists()) {
                // libsignal expects a fresh SessionRecord if none exists yet
                val fresh = SessionRecord()
                cache[key] = fresh
                return fresh
            }

            val record = SessionRecord(file.readBytes())
            cache[key] = record
            return record
        }

        override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
            return addresses.mapNotNull { address ->
                val key = addressKey(address)
                cache[key]?.let { return@mapNotNull it }

                val file = sessionFile(address)
                if (!file.exists()) return@mapNotNull null

                val record = SessionRecord(file.readBytes())
                cache[key] = record
                record
            }
        }

        override fun getSubDeviceSessions(name: String): List<Int> {
            val prefix = "${name}_"
            val fromCache =
                cache.keys
                    .filter { it.startsWith(prefix) }
                    .mapNotNull { it.removePrefix(prefix).toIntOrNull() }

            val fromDisk =
                sessionDir
                    .listFiles()
                    ?.filter { it.nameWithoutExtension.startsWith(prefix) }
                    ?.mapNotNull { it.nameWithoutExtension.removePrefix(prefix).toIntOrNull() }
                    ?: emptyList()

            return (fromCache + fromDisk).distinct().filter { it != 1 }
        }

        override fun storeSession(
            address: SignalProtocolAddress,
            record: SessionRecord,
        ) {
            val key = addressKey(address)
            sessionFile(address).writeBytes(record.serialize())
            cache[key] = record
        }

        override fun containsSession(address: SignalProtocolAddress): Boolean {
            val key = addressKey(address)
            return cache.containsKey(key) || sessionFile(address).exists()
        }

        override fun deleteSession(address: SignalProtocolAddress) {
            cache.remove(addressKey(address))
            sessionFile(address).delete()
        }

        override fun deleteAllSessions(name: String) {
            val prefix = "${name}_"

            val keysToRemove = cache.keys.filter { it.startsWith(prefix) }
            keysToRemove.forEach { cache.remove(it) }

            sessionDir
                .listFiles()
                ?.filter { it.nameWithoutExtension.startsWith(prefix) }
                ?.forEach { it.delete() }
        }

        /**
         * Wipes all session data. Called on account deletion.
         */
        fun wipeAll() {
            cache.clear()
            sessionDir.listFiles()?.forEach { it.delete() }
        }

        private fun addressKey(address: SignalProtocolAddress): String = "${address.name}_${address.deviceId}"

        private fun sessionFile(address: SignalProtocolAddress): File = File(sessionDir, "${address.name}_${address.deviceId}.bin")
    }
