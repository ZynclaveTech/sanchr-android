package com.sanchr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.sanchr.core.notifications.NewMessageNotifier
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.sync.SyncWorker
import com.sanchr.sync.realtime.RealtimeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SanchrApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHandler: NotificationHandler

    @Inject
    lateinit var realtimeManager: RealtimeManager

    @Inject
    lateinit var newMessageNotifier: NewMessageNotifier

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()

    override fun onCreate() {
        super.onCreate()

        // Create notification channels as early as possible so they are available
        // before any push notification arrives.
        notificationHandler.createNotificationChannels()
        realtimeManager.initialize()
        // The notifier runs on the Hilt-provided @ApplicationScope — no ad-hoc
        // CoroutineScope here, so every singleton observer shares one
        // SupervisorJob and one dispatcher pool.
        newMessageNotifier.start()

        // Schedule periodic background sync (every 15 minutes).
        // This is a fallback; SyncInitializer via App Startup also schedules
        // periodic sync, but calling schedulePeriodic with KEEP policy is
        // idempotent and harmless if already enqueued.
        SyncWorker.schedulePeriodic(WorkManager.getInstance(this))
    }
}
