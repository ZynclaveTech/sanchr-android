package com.sanchr.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.notifierStateDataStore by preferencesDataStore(name = "notifier_state")

/**
 * Durable bookkeeping for the new-message notifier.
 *
 * The notifier observes freshly-decrypted rows inserted by
 * `ReceiveMessageUseCase` and renders a system notification for each one.
 * On a cold start we must not re-notify for rows that the user already
 * saw on the previous run — otherwise every process restart would fire a
 * re-notification flood for every message decrypted since account
 * registration.
 *
 * This class persists the timestamp of the most recently-notified message
 * so the observer can resume at the correct high-water mark. A separate
 * DataStore from `UserPreferences` keeps this runtime bookkeeping out of
 * the user-facing settings surface.
 */
@Singleton
class NotifierState
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.notifierStateDataStore

        private object Keys {
            val LAST_NOTIFIED_TIMESTAMP = longPreferencesKey("last_notified_timestamp")
        }

        /**
         * Returns the epoch-millis timestamp of the last message the notifier
         * rendered, or `null` if the notifier has never run on this install.
         */
        suspend fun getLastNotifiedTimestamp(): Long? = dataStore.data.first()[Keys.LAST_NOTIFIED_TIMESTAMP]

        /**
         * Advances the high-water mark. Idempotent — a call with a smaller
         * value than the stored one is a no-op (guards against out-of-order
         * bursts from `collectLatest`).
         */
        suspend fun advanceLastNotifiedTimestamp(timestamp: Long) {
            dataStore.edit { prefs ->
                val current = prefs[Keys.LAST_NOTIFIED_TIMESTAMP] ?: 0L
                if (timestamp > current) {
                    prefs[Keys.LAST_NOTIFIED_TIMESTAMP] = timestamp
                }
            }
        }
    }
