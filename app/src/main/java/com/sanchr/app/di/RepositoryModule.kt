package com.sanchr.app.di

import com.sanchr.app.data.CallRepositoryImpl
import com.sanchr.app.data.ContactRepositoryImpl
import com.sanchr.app.data.DiscoveryRepositoryImpl
import com.sanchr.app.data.MessageRepositoryImpl
import com.sanchr.app.data.VaultRepositoryImpl
import com.sanchr.domain.calls.CallRepository
import com.sanchr.domain.contacts.ContactRepository
import com.sanchr.domain.contacts.DiscoveryRepository
import com.sanchr.domain.messaging.MessageRepository
import com.sanchr.domain.vault.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindDiscoveryRepository(impl: DiscoveryRepositoryImpl): DiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindCallRepository(impl: CallRepositoryImpl): CallRepository
}
