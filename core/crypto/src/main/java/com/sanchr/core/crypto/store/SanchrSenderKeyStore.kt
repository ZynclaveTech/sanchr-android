package com.sanchr.core.crypto.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based sender key store for group messaging. Each sender key is persisted
 * under `signal/sender_keys/{userId}_{deviceId}_{distributionId}.bin`.
 *
 * Sender keys enable efficient group encryption where a single encrypt operation
 * produces ciphertext readable by all group members, rather than per-member encryption.
 */
@Singleton
class SanchrSenderKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SenderKeyStore {

    private val senderKeyDir: File by lazy {
        File(context.filesDir, "signal/sender_keys").also { it.mkdirs() }
    }

    private val cache = ConcurrentHashMap<String, SenderKeyRecord>()

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) {
        val key = senderKeyKey(sender, distributionId)
        senderKeyFile(sender, distributionId).writeBytes(record.serialize())
        cache[key] = record
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
    ): SenderKeyRecord? {
        val key = senderKeyKey(sender, distributionId)
        cache[key]?.let { return it }

        val file = senderKeyFile(sender, distributionId)
        if (!file.exists()) return null

        val record = SenderKeyRecord(file.readBytes())
        cache[key] = record
        return record
    }

    /**
     * Wipes all sender key data. Called on account deletion.
     */
    fun wipeAll() {
        cache.clear()
        senderKeyDir.listFiles()?.forEach { it.delete() }
    }

    private fun senderKeyKey(sender: SignalProtocolAddress, distributionId: UUID): String =
        "${sender.name}_${sender.deviceId}_$distributionId"

    private fun senderKeyFile(sender: SignalProtocolAddress, distributionId: UUID): File =
        File(senderKeyDir, "${sender.name}_${sender.deviceId}_$distributionId.bin")
}
