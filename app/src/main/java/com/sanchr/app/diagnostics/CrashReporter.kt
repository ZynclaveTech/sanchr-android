package com.sanchr.app.diagnostics

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.common.diagnostics.CrashReportRedaction
import com.sanchr.core.datastore.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Crash reporting, off unless the user turns it on.
 *
 * Until this existed a crash in the field left no trace at all: no stack,
 * no count, no way to tell a one-off from a pattern.
 *
 * Two things are deliberate. Collection is disabled at rest and only
 * enabled once the preference says so, so a user who never opts in sends
 * nothing even on the launch that crashes. And no user identifier is ever
 * attached — Crashlytics offers `setUserId`, and using it in a messenger
 * would let a third party count crashes per person.
 */
@Singleton
class CrashReporter
    @Inject
    constructor(
        private val userPreferences: UserPreferences,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val crashlytics by lazy { runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() }

        /**
         * Follows the preference for the life of the process, so revoking
         * consent stops collection immediately rather than at next launch.
         */
        fun start() {
            applicationScope.launch {
                userPreferences.crashReportingEnabled.collectLatest { enabled ->
                    runCatching { crashlytics?.isCrashlyticsCollectionEnabled = enabled }
                        .onFailure { Log.w(TAG, "could not apply crash reporting consent") }
                }
            }
        }

        /**
         * Records a handled error worth knowing about.
         *
         * The message is replaced by a type-and-site summary: a caught
         * exception's text in this app can hold a phone number or a message
         * body, and none of that may leave the device.
         */
        fun recordHandled(
            throwable: Throwable,
            context: String? = null,
        ) {
            val reporter = crashlytics ?: return
            runCatching {
                context?.let { reporter.log(CrashReportRedaction.redact(it)) }
                reporter.log(CrashReportRedaction.summarise(throwable))
                reporter.recordException(throwable)
            }
        }

        private companion object {
            const val TAG = "CrashReporter"
        }
    }
