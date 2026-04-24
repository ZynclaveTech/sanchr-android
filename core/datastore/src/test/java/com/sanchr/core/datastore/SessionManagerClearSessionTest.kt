package com.sanchr.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that [SessionManager.clearSession] wipes every persisted key.
 *
 * `EncryptedSharedPreferences` requires the Android Keystore which is not
 * exposed under Robolectric, so we inject a plain [SharedPreferences] into
 * the private `encryptedPrefs` lazy delegate via reflection. This keeps the
 * production public API untouched while letting us assert the full-wipe
 * contract on real `SharedPreferences` semantics.
 */
@RunWith(RobolectricTestRunner::class)
class SessionManagerClearSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val allKeys: List<String> =
        listOf(
            "access_token",
            "refresh_token",
            "user_id",
            "device_id",
            "token_expiry",
            "installation_id",
            "device_master_secret",
            "recovery_key",
            "backup_enabled",
            "backup_lineage_id",
            "backup_format_version",
            "backup_confirmed_at",
            "backup_last_at",
            "backup_last_content_hash",
            "sender_certificate_b64",
            "account_password",
            "display_name",
        )

    private fun newSessionManagerWith(prefs: SharedPreferences): SessionManager {
        val sm = SessionManager(context)
        val field = SessionManager::class.java.getDeclaredField("encryptedPrefs\$delegate")
        field.isAccessible = true
        // Replace the Lazy<SharedPreferences> with an eagerly-resolved one.
        field.set(sm, lazy { prefs })
        return sm
    }

    @Test
    fun `clearSession removes every persisted key`() {
        val prefs = context.getSharedPreferences("session_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Seed every key with a non-null value so the absence check is meaningful.
        val editor = prefs.edit()
        editor.putString("access_token", "a")
        editor.putString("refresh_token", "r")
        editor.putString("user_id", "u")
        editor.putString("device_id", "d")
        editor.putLong("token_expiry", 123L)
        editor.putString("installation_id", "i")
        editor.putString("device_master_secret", "dms")
        editor.putString("recovery_key", "rk")
        editor.putBoolean("backup_enabled", true)
        editor.putString("backup_lineage_id", "bli")
        editor.putInt("backup_format_version", 1)
        editor.putLong("backup_confirmed_at", 1L)
        editor.putLong("backup_last_at", 2L)
        editor.putString("backup_last_content_hash", "h")
        editor.putString("sender_certificate_b64", "sc")
        editor.putString("account_password", "p")
        editor.putString("display_name", "n")
        editor.apply()

        // Sanity: everything was written.
        allKeys.forEach { key ->
            assertTrue(prefs.contains(key), "expected seeded key '$key' present before clearSession")
        }

        val sessionManager = newSessionManagerWith(prefs)
        sessionManager.clearSession()

        allKeys.forEach { key ->
            assertFalse(prefs.contains(key), "expected key '$key' to be absent after clearSession")
        }
    }
}
