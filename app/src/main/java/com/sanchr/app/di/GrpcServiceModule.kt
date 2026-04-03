package com.sanchr.app.di

import com.sanchr.core.network.GrpcChannelProvider
import com.sanchr.proto.auth.AuthServiceClient
import com.sanchr.proto.auth.AuthServiceGrpcClient
import com.sanchr.proto.calling.CallSignalingServiceClient
import com.sanchr.proto.calling.CallSignalingServiceGrpcClient
import com.sanchr.proto.contacts.ContactServiceClient
import com.sanchr.proto.contacts.ContactServiceGrpcClient
import com.sanchr.proto.keys.KeyServiceClient
import com.sanchr.proto.keys.KeyServiceGrpcClient
import com.sanchr.proto.media.MediaServiceClient
import com.sanchr.proto.media.MediaServiceGrpcClient
import com.sanchr.proto.messaging.MessagingServiceClient
import com.sanchr.proto.messaging.MessagingServiceGrpcClient
import com.sanchr.proto.notifications.NotificationServiceClient
import com.sanchr.proto.notifications.NotificationServiceGrpcClient
import com.sanchr.proto.settings.SettingsServiceClient
import com.sanchr.proto.settings.SettingsServiceGrpcClient
import com.sanchr.proto.vault.VaultServiceClient
import com.sanchr.proto.vault.VaultServiceGrpcClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides all gRPC service client implementations bound to their interfaces.
 * Each client receives the shared [ManagedChannel] from [GrpcChannelProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object GrpcServiceModule {

    @Provides
    @Singleton
    fun provideAuthServiceClient(
        channelProvider: GrpcChannelProvider,
    ): AuthServiceClient = AuthServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideKeyServiceClient(
        channelProvider: GrpcChannelProvider,
    ): KeyServiceClient = KeyServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideContactServiceClient(
        channelProvider: GrpcChannelProvider,
    ): ContactServiceClient = ContactServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideSettingsServiceClient(
        channelProvider: GrpcChannelProvider,
    ): SettingsServiceClient = SettingsServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideMediaServiceClient(
        channelProvider: GrpcChannelProvider,
    ): MediaServiceClient = MediaServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideVaultServiceClient(
        channelProvider: GrpcChannelProvider,
    ): VaultServiceClient = VaultServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideMessagingServiceClient(
        channelProvider: GrpcChannelProvider,
    ): MessagingServiceClient = MessagingServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideNotificationServiceClient(
        channelProvider: GrpcChannelProvider,
    ): NotificationServiceClient = NotificationServiceGrpcClient(channelProvider.getChannel())

    @Provides
    @Singleton
    fun provideCallSignalingServiceClient(
        channelProvider: GrpcChannelProvider,
    ): CallSignalingServiceClient = CallSignalingServiceGrpcClient(channelProvider.getChannel())
}
