package com.sanchr.core.crypto.di

import com.sanchr.core.crypto.sealed.BuildConfigTrustRootProvider
import com.sanchr.core.crypto.sealed.TrustRootProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the sealed-sender [TrustRootProvider] to the build-config-backed
 * default. Tests that exercise sealed-sender supply their own provider via
 * constructor injection of [com.sanchr.core.crypto.sealed.SealedSenderCipher]
 * and do not need this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SealedSenderModule {
    @Binds
    @Singleton
    abstract fun bindTrustRootProvider(impl: BuildConfigTrustRootProvider): TrustRootProvider
}
