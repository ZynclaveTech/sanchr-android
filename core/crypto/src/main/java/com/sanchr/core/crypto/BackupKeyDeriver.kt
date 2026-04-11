package com.sanchr.core.crypto

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.messagebackup.AccountEntropyPool
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.protocol.ServiceId

data class DerivedBackupMaterial(
    val metadataKey: ByteArray,
    val aesKey: ByteArray?,
    val hmacKey: ByteArray?,
    val backupId: ByteArray?,
)

interface BackupKeyDeriver {
    fun deriveMaterial(recoveryKey: String, userId: String?): DerivedBackupMaterial
}

@Singleton
class SignalBackupKeyDeriver @Inject constructor() : BackupKeyDeriver {
    override fun deriveMaterial(recoveryKey: String, userId: String?): DerivedBackupMaterial {
        require(AccountEntropyPool.isValid(recoveryKey)) { "Recovery key is invalid" }

        val backupKey = AccountEntropyPool.deriveBackupKey(recoveryKey)
        val metadataKey = backupKey.deriveLocalBackupMetadataKey()

        val uuid = userId?.let(UUID::fromString)
        if (uuid == null) {
            return DerivedBackupMaterial(
                metadataKey = metadataKey,
                aesKey = null,
                hmacKey = null,
                backupId = null,
            )
        }

        val aci = ServiceId.Aci(uuid)
        val messageBackupKey = MessageBackupKey(recoveryKey, aci, null)
        return DerivedBackupMaterial(
            metadataKey = metadataKey,
            aesKey = messageBackupKey.aesKey,
            hmacKey = messageBackupKey.hmacKey,
            backupId = backupKey.deriveBackupId(aci),
        )
    }
}
