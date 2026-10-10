package com.sanchr.sync.vault

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.domain.messaging.media.AttachmentException
import com.sanchr.domain.messaging.vault.AutoVaultScheduler
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Runs [AutoVaultIncoming] for one message, with a network and with retries.
 *
 * A transport failure is retried, a few times; anything else is not. In every
 * failing case the message stays in the chat as it arrived, which is the safe
 * way to be wrong: the media is still there, just not vaulted.
 */
@HiltWorker
class AutoVaultWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val autoVaultIncoming: AutoVaultIncoming,
        private val dispatchers: DispatcherProvider,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
            return withContext(dispatchers.io) {
                try {
                    autoVaultIncoming(messageId)
                    Result.success()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AttachmentException) {
                    // Gone from the server or undecryptable: trying again
                    // will not change that.
                    Log.w(TAG, "auto-vault abandoned: ${e.message}")
                    Result.failure()
                } catch (e: IOException) {
                    Log.w(TAG, "auto-vault attempt $runAttemptCount failed: ${e.message}")
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                } catch (e: Exception) {
                    Log.w(TAG, "auto-vault failed", e)
                    Result.failure()
                }
            }
        }

        companion object {
            private const val TAG = "AutoVaultWorker"
            private const val KEY_MESSAGE_ID = "message_id"
            private const val MAX_RETRIES = 3

            fun enqueue(
                context: Context,
                messageId: String,
            ) {
                val request =
                    OneTimeWorkRequestBuilder<AutoVaultWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
                        .build()
                // One job per message, and a redelivered message does not
                // start a second: two would each add the media to the vault.
                WorkManager.getInstance(context).enqueueUniqueWork("auto-vault-$messageId", ExistingWorkPolicy.KEEP, request)
            }
        }
    }

@Singleton
class AutoVaultSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AutoVaultScheduler {
        override fun schedule(messageId: String) = AutoVaultWorker.enqueue(context, messageId)
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AutoVaultSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindScheduler(impl: AutoVaultSchedulerImpl): AutoVaultScheduler
}
