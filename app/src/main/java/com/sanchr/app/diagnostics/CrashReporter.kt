package com.sanchr.app.diagnostics

import android.content.Context
import android.util.Log
import com.sanchr.app.BuildConfig
import com.sanchr.core.common.di.ApplicationScope
import com.sanchr.core.common.diagnostics.CrashReportRedaction
import com.sanchr.core.datastore.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Crash reporting to our own Sentry, off unless the user turns it on.
 *
 * Sentry, self-hosted, rather than Crashlytics — because of who receives the
 * report, not because of what it contains. Crashlytics depends on Firebase
 * Installations, which mints a per-install identifier and sends it to Google
 * with every event; declining to call `setUserId` does not prevent that.
 * Signal takes no automatic crash reports at all, and WhatsApp sends to
 * Meta's own infrastructure. Both reject the shape Crashlytics has. Reporting
 * to infrastructure we run keeps the operational value without giving a third
 * party a stable identifier for every install.
 *
 * Inert when the DSN is empty, which is the default, so a fork or a local
 * build reports nowhere.
 */
@Singleton
class CrashReporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userPreferences: UserPreferences,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private var initialised = false

        /**
         * Follows the preference for the life of the process, so revoking
         * consent stops collection immediately rather than at next launch.
         *
         * The SDK is initialised only once consent exists. A user who never
         * opts in has no Sentry client at all, so nothing can be sent even on
         * the launch that crashes.
         */
        fun start() {
            if (BuildConfig.SENTRY_DSN.isBlank()) {
                Log.i(TAG, "No Sentry DSN in this build; crash reporting is unavailable")
                return
            }
            applicationScope.launch {
                userPreferences.crashReportingEnabled.collectLatest { enabled ->
                    when {
                        enabled && !initialised -> initialise()
                        !enabled && initialised -> shutDown()
                        else -> Unit
                    }
                }
            }
        }

        /**
         * Records a handled error worth knowing about.
         *
         * The exception's own message is never sent: a caught error's text in
         * this app can hold a phone number or a message body, so only a
         * type-and-site summary goes out.
         */
        fun recordHandled(
            throwable: Throwable,
            context: String? = null,
        ) {
            if (!initialised) return
            runCatching {
                Sentry.addBreadcrumb(CrashReportRedaction.summarise(throwable))
                context?.let { Sentry.addBreadcrumb(CrashReportRedaction.redact(it)) }
                Sentry.captureException(throwable)
            }
        }

        /**
         * Closing rather than flagging: it stops the worker and drops what is
         * queued, so consent withdrawn means nothing already captured still
         * reaches the server.
         */
        private fun shutDown() {
            runCatching { Sentry.close() }
            initialised = false
        }

        private fun initialise() {
            runCatching {
                SentryAndroid.init(context) { options -> options.applyPrivacyDefaults() }
                initialised = true
            }.onFailure { Log.w(TAG, "Sentry init failed; crash reporting stays off") }
        }

        private fun SentryAndroidOptions.applyPrivacyDefaults() {
            dsn = BuildConfig.SENTRY_DSN
            release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"

            // Stops the client attaching the device's IP address and similar.
            // The server sees the IP regardless, so it must also be set to
            // discard it; this is the half we control from here.
            isSendDefaultPii = false

            // A screenshot or view hierarchy of a messenger is message
            // content. Never.
            isAttachScreenshot = false
            isAttachViewHierarchy = false

            // Automatic breadcrumbs trace UI and network activity, which here
            // means conversation ids and request URLs. Only the ones added
            // deliberately, already redacted.
            isEnableAutoSessionTracking = false
            isEnableUserInteractionBreadcrumbs = false

            // Last line of defence. An exception message is free text that
            // could hold anything, so it is redacted on the way out even
            // though recordHandled already summarises.
            beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _ ->
                    event.message?.let { it.formatted = CrashReportRedaction.redact(it.formatted) }
                    // Sentry otherwise infers a user from the install.
                    // Dropping it is what stops per-person crash counting.
                    event.user = null
                    event
                }
        }

        private companion object {
            const val TAG = "CrashReporter"
        }
    }
