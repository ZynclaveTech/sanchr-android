package com.sanchr.core.network.di

import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.network.GrpcChannelProvider
import com.sanchr.core.network.SessionRefresher
import com.sanchr.core.network.UnauthenticatedRefreshInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Hilt wiring for the :core:network module.
 *
 * Exposes the shared core [ManagedChannel] and an [UnauthenticatedRefreshInterceptor.OnUnauthenticated]
 * callback that kicks off [SessionRefresher.refresh] on the app-wide
 * [ApplicationScope] whenever a call comes back UNAUTHENTICATED.
 *
 * [SessionRefresher] is injected as a [Provider]: it depends (via
 * [com.sanchr.proto.auth.AuthServiceClient]) on the same core channel that
 * [GrpcChannelProvider] builds with this interceptor attached, so resolving
 * it eagerly here would be a dependency cycle. A [Provider] defers
 * construction until the callback actually fires, by which point the
 * channel already exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideManagedChannel(channelProvider: GrpcChannelProvider): ManagedChannel = channelProvider.getCoreChannel()

    @Provides
    @Singleton
    fun provideOnUnauthenticated(
        refresher: Provider<SessionRefresher>,
        @ApplicationScope scope: CoroutineScope,
    ): UnauthenticatedRefreshInterceptor.OnUnauthenticated =
        UnauthenticatedRefreshInterceptor.OnUnauthenticated { scope.launch { refresher.get().refresh() } }
}
