package com.sanchr.app.di

import com.sanchr.core.callengine.CallManager
import com.sanchr.core.common.calls.CallPeerNames
import com.sanchr.core.common.calls.IncomingCallEvents
import com.sanchr.domain.messaging.ContactProfileResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the call engine to the rest of the app: the realtime stream hands
 * call events to [CallManager], and the call screen names peers through
 * the same precedence as chats (`ContactDisplayName`).
 */
@Module
@InstallIn(SingletonComponent::class)
object CallModule {
    @Provides
    @Singleton
    fun provideIncomingCallEvents(callManager: CallManager): IncomingCallEvents = callManager

    @Provides
    @Singleton
    fun provideCallPeerNames(resolver: ContactProfileResolver): CallPeerNames = CallPeerNames { resolver.displayNameFor(it) }
}
