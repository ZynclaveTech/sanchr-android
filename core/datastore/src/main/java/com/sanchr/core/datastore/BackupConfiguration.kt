package com.sanchr.core.datastore

data class BackupConfiguration(
    val isEnabled: Boolean,
    val lineageId: String,
    val formatVersion: Int = 1,
    val recoveryKeyConfirmedAtMillis: Long,
    val lastBackupAtMillis: Long? = null,
    val lastBackupContentHash: String? = null,
)
