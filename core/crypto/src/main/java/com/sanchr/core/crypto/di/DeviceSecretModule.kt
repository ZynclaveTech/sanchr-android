package com.sanchr.core.crypto.di

import com.sanchr.core.crypto.AndroidDeviceSecretProvider
import com.sanchr.core.crypto.DeviceSecretProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceSecretModule {
    @Binds
    abstract fun bindDeviceSecretProvider(impl: AndroidDeviceSecretProvider): DeviceSecretProvider
}
