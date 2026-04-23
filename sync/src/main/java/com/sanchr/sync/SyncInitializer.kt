package com.sanchr.sync

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.work.WorkManager
import androidx.work.WorkManagerInitializer
import com.sanchr.sync.rotation.PreKeyReplenishWorker
import com.sanchr.sync.rotation.SenderCertificateRotationWorker
import com.sanchr.sync.rotation.SignedPreKeyRotationWorker

/**
 * App Startup [Initializer] that automatically schedules the periodic
 * background sync when the application process starts.
 *
 * Declared in the app module's AndroidManifest.xml via the
 * `androidx.startup.InitializationProvider` content provider.
 *
 * Depends on [WorkManagerInitializer] so that [WorkManager] is ready
 * before we attempt to enqueue work.
 */
class SyncInitializer : Initializer<Unit> {
    companion object {
        private const val TAG = "SyncInitializer"
    }

    override fun create(context: Context) {
        val workManager = WorkManager.getInstance(context)
        SyncWorker.schedulePeriodic(workManager)
        SendRetryWorker.schedulePeriodic(workManager)
        SignedPreKeyRotationWorker.schedulePeriodic(workManager)
        SenderCertificateRotationWorker.schedulePeriodic(workManager)
        PreKeyReplenishWorker.schedulePeriodic(workManager)
        Log.i(TAG, "Periodic sync + send-retry + rotation workers initialized via App Startup")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(
            WorkManagerInitializer::class.java,
        )
}
