package com.sanchr.app.smoke

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts Sentry never starts itself.
 *
 * The SDK ships `SentryInitProvider`, a ContentProvider that initialises
 * Sentry before `Application.onCreate`. That is wrong here twice over:
 * it would start collecting before `CrashReporter` has read the user's
 * consent, and with no DSN in the manifest — the default, since the DSN is
 * injected at build time — it throws `DSN is required` and kills the process
 * at launch. That crash is how this was found (run 34006416590).
 *
 * `StartupSmokeTest` would catch the crash again, but not the quieter
 * regression: a build that does carry a DSN would start reporting without
 * consent and still reach RESUMED. This test names the actual invariant.
 */
@SmallTest
@SmokeTest
@RunWith(AndroidJUnit4::class)
class SentryAutoInitDisabledTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sentryAutoInitProvidersAreNotInstalled() {
        val providers =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
                .providers
                .orEmpty()
                .map { it.name }

        assertFalse(
            "SentryInitProvider is installed; Sentry would start before the consent check",
            providers.any { it.startsWith("io.sentry.") },
        )
    }

    @Test
    fun autoInitMetaDataIsOff() {
        val metaData =
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData

        assertEquals(false, metaData?.get("io.sentry.auto-init"))
    }
}
