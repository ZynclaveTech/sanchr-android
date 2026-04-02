package com.sanchr.app.di

import android.content.Context
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.StandardDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = StandardDispatcherProvider()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        dispatcherProvider: DispatcherProvider,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)

    // TODO: Provide app-level configuration (base URL, feature flags, etc.)
}
