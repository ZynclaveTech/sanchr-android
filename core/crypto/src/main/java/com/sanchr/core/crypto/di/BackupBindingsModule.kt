package com.sanchr.core.crypto.di

import com.sanchr.core.crypto.BackupKeyDeriver
import com.sanchr.core.crypto.RecoveryKeyManager
import com.sanchr.core.crypto.SignalBackupKeyDeriver
import com.sanchr.core.crypto.SignalRecoveryKeyManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupBindingsModule {

    @Binds
    @Singleton
    abstract fun bindBackupKeyDeriver(impl: SignalBackupKeyDeriver): BackupKeyDeriver

    @Binds
    @Singleton
    abstract fun bindRecoveryKeyManager(impl: SignalRecoveryKeyManager): RecoveryKeyManager
}
