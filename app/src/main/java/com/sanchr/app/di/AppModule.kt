package com.sanchr.app.di

import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.common.di.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Long-lived app-wide coroutine scope used by singleton observers that
     * outlive any Activity (for example `NewMessageNotifier`). Backed by a
     * [SupervisorJob] so one child failure does not cancel siblings; runs
     * on the default dispatcher — callers should switch to IO themselves
     * for blocking work.
     *
     * The `@ApplicationScope` qualifier lives in `:core:common` so
     * downstream modules can inject this scope without depending on
     * `:app`.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcherProvider.default)
}
