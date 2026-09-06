package com.sanchr.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.sanchr.app.diagnostics.CrashReporter
import com.sanchr.core.notifications.NewMessageNotifier
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.sync.SendRetryWorker
import com.sanchr.sync.SyncWorker
import com.sanchr.sync.realtime.RealtimeManager
import com.sanchr.sync.rotation.PreKeyReplenishWorker
import com.sanchr.sync.rotation.SenderCertificateRotationWorker
import com.sanchr.sync.rotation.SignedPreKeyRotationWorker
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

    @Inject
    lateinit var crashReporter: CrashReporter

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
        // Before anything else that could throw, so a crash during startup
        // is still covered once the user has opted in.
        crashReporter.start()
        notificationHandler.createNotificationChannels()
        realtimeManager.initialize()
        // The notifier runs on the Hilt-provided @ApplicationScope — no ad-hoc
        // CoroutineScope here, so every singleton observer shares one
        // SupervisorJob and one dispatcher pool.
        newMessageNotifier.start()

        // Every periodic worker is scheduled here, and only here.
        //
        // It used to be scheduled from an App Startup initializer as well.
        // Initializers run from a ContentProvider, before Application.onCreate,
        // so Hilt had not yet injected `workerFactory`; that initializer named
        // WorkManagerInitializer as a dependency, App Startup ran it directly
        // regardless of the manifest removing it, and WorkManager was pinned to
        // its default configuration before this class was ever consulted.
        // HiltWorkerFactory was therefore never installed and every @HiltWorker
        // failed to construct for the life of the process.
        //
        // Reaching WorkManager for the first time here, after super.onCreate()
        // has injected the factory, is what makes on-demand initialization pick
        // up the configuration above.
        val workManager = WorkManager.getInstance(this)
        SyncWorker.schedulePeriodic(workManager)
        SendRetryWorker.schedulePeriodic(workManager)
        SignedPreKeyRotationWorker.schedulePeriodic(workManager)
        SenderCertificateRotationWorker.schedulePeriodic(workManager)
        PreKeyReplenishWorker.schedulePeriodic(workManager)
    }
}
