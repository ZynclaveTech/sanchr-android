package com.sanchr.core.crypto

import com.sanchr.core.datastore.BackupConfiguration
import com.sanchr.core.datastore.SessionManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.messagebackup.AccountEntropyPool

interface RecoveryKeyManager {
    fun loadConfiguration(): BackupConfiguration?
    fun readRecoveryKey(): String?
    fun generateRecoveryKey(): String
    fun enableBackups(recoveryKey: String, lineageId: String = UUID.randomUUID().toString().lowercase()): BackupConfiguration
    fun disableBackups()
    fun updateBackupState(timestampMillis: Long?, contentHash: String?)
    fun persistRestoredBackup(
        recoveryKey: String,
        lineageId: String,
        formatVersion: Int,
        lastBackupAtMillis: Long?,
        lastBackupContentHash: String?,
    ): BackupConfiguration
    fun clearBackupMaterial()
}

@Singleton
class SignalRecoveryKeyManager @Inject constructor(
    private val sessionManager: SessionManager,
) : RecoveryKeyManager {
    override fun loadConfiguration(): BackupConfiguration? = sessionManager.getBackupConfiguration()

    override fun readRecoveryKey(): String? = sessionManager.getRecoveryKey()

    override fun generateRecoveryKey(): String {
        val recoveryKey = AccountEntropyPool.generate()
        check(AccountEntropyPool.isValid(recoveryKey)) { "Generated invalid recovery key" }
        return recoveryKey
    }

    override fun enableBackups(recoveryKey: String, lineageId: String): BackupConfiguration {
        require(AccountEntropyPool.isValid(recoveryKey)) { "Recovery key is invalid" }

        val configuration = BackupConfiguration(
            isEnabled = true,
            lineageId = lineageId,
            formatVersion = 1,
            recoveryKeyConfirmedAtMillis = System.currentTimeMillis(),
            lastBackupAtMillis = null,
            lastBackupContentHash = null,
        )
        sessionManager.saveRecoveryKey(recoveryKey)
        sessionManager.saveBackupConfiguration(configuration)
        return configuration
    }

    override fun disableBackups() {
        sessionManager.clearBackupMaterial()
    }

    override fun updateBackupState(timestampMillis: Long?, contentHash: String?) {
        val current = loadConfiguration() ?: return
        sessionManager.saveBackupConfiguration(
            current.copy(
                lastBackupAtMillis = timestampMillis,
                lastBackupContentHash = contentHash,
            ),
        )
    }

    override fun persistRestoredBackup(
        recoveryKey: String,
        lineageId: String,
        formatVersion: Int,
        lastBackupAtMillis: Long?,
        lastBackupContentHash: String?,
    ): BackupConfiguration {
        require(AccountEntropyPool.isValid(recoveryKey)) { "Recovery key is invalid" }

        val configuration = BackupConfiguration(
            isEnabled = true,
            lineageId = lineageId,
            formatVersion = formatVersion,
            recoveryKeyConfirmedAtMillis = System.currentTimeMillis(),
            lastBackupAtMillis = lastBackupAtMillis,
            lastBackupContentHash = lastBackupContentHash,
        )
        sessionManager.saveRecoveryKey(recoveryKey)
        sessionManager.saveBackupConfiguration(configuration)
        return configuration
    }

    override fun clearBackupMaterial() {
        sessionManager.clearBackupMaterial()
    }
}
