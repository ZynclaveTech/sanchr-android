package com.sanchr.app.bootstrap

import android.util.Log
import com.sanchr.core.common.DispatcherProvider
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.proto.settings.GetSettingsRequest
import com.sanchr.proto.settings.SettingsServiceClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Brings this phone up to date, at launch, with what the server holds about
 * Sanchr Mode.
 *
 * The mode is enforced from the phone's own copy of the flag, and that copy
 * was only refreshed when the Settings screen was opened. Turned on from
 * another device, the mode did nothing here — receipts, typing and presence
 * all carried on going out — until this phone's owner happened to open
 * Settings.
 *
 * Only Sanchr Mode is taken from the server, and on purpose. It is changed by
 * a call that either reaches the server or visibly fails, so the server's
 * value is the true one. The individual toggles — read receipts, typing,
 * online status — are written here first and sent to the server afterwards,
 * on a delay that does not survive the app being closed. Taking the server's
 * word for those at launch would undo a choice made offline, and the choice
 * most at risk is someone turning a thing *off*.
 *
 * A failure leaves the phone's copy as it was: enforcing a slightly old
 * answer is better than enforcing none.
 */
@Singleton
class ServerSettingsLoader
    @Inject
    constructor(
        private val settingsServiceClient: SettingsServiceClient,
        private val userPreferences: UserPreferences,
        private val dispatchers: DispatcherProvider,
    ) {
        /** @return whether the server was reached and the phone's copy brought up to date. */
        suspend fun load(): Boolean =
            withContext(dispatchers.io) {
                try {
                    val settings = settingsServiceClient.getSettings(GetSettingsRequest(userId = ""))
                    userPreferences.setSanchrModeEnabled(settings.sanchrModeEnabled)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "could not load settings at launch; keeping what this phone has", e)
                    false
                }
            }

        private companion object {
            const val TAG = "ServerSettingsLoader"
        }
    }
