package com.sanchr.core.crypto.di

import com.sanchr.core.crypto.oprf.Oprf
import com.sanchr.core.crypto.oprf.OprfClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OprfModule {
    @Binds
    abstract fun bindOprf(impl: OprfClient): Oprf
}
