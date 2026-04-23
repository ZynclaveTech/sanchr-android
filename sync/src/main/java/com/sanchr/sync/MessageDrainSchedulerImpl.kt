package com.sanchr.sync

import android.content.Context
import com.sanchr.core.notifications.MessageDrainScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync-side implementation of [MessageDrainScheduler]. Enqueues the
 * expedited [MessageDrainWorker] via WorkManager.
 */
@Singleton
class MessageDrainSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MessageDrainScheduler {
        override fun enqueueDrain() {
            MessageDrainWorker.enqueue(context)
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MessageDrainSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindScheduler(impl: MessageDrainSchedulerImpl): MessageDrainScheduler
}
