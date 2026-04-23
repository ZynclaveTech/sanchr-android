package com.sanchr.core.common.di

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.StandardDispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: StandardDispatcherProvider): DispatcherProvider
}
