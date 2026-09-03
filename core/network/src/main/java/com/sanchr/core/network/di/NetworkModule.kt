package com.sanchr.core.network.di

import com.sanchr.core.network.GrpcChannelProvider
import com.sanchr.core.network.UnauthenticatedRefreshInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import javax.inject.Singleton

/**
 * Hilt wiring for the :core:network module.
 *
 * Exposes the shared core [ManagedChannel] and a default no-op
 * [UnauthenticatedRefreshInterceptor.OnUnauthenticated] callback. The real
 * refresh / sign-in flow replaces the callback in M4 via a higher-priority
 * Hilt binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideManagedChannel(channelProvider: GrpcChannelProvider): ManagedChannel = channelProvider.getCoreChannel()

    @Provides
    @Singleton
    fun provideOnUnauthenticated(): UnauthenticatedRefreshInterceptor.OnUnauthenticated =
        UnauthenticatedRefreshInterceptor.OnUnauthenticated { /* no-op until M4 wires the refresh flow */ }
}
