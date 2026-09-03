package com.sanchr.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
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

    /**
     * Regression guard for review finding P1#2 (Phase 8).
     *
     * `clearSession` MUST use `.commit()` — synchronous + fsync — rather than
     * `.apply()` — async — so the disk wipe is guaranteed to be durable
     * before the in-memory `_isAuthenticated` flag flips to `false`. If a
     * crash occurred between an `.apply()`-style flag flip and the
     * background disk write, tokens would survive on disk while `NavHost`
     * believed logout had happened — next cold start would silently re-auth
     * the "logged-out" user from on-disk tokens.
     *
     * We wrap the underlying [SharedPreferences] with a delegating proxy
     * whose `edit()` returns a counting editor. The counting editor
     * delegates every mutator call to a real editor so state wipes really
     * happen, but tracks whether `commit()` or `apply()` was ultimately
     * invoked to terminate the chain.
     */
    @Test
    fun `clearSession uses commit not apply to guarantee durability before flag flip`() {
        val realPrefs = context.getSharedPreferences("session_commit_test", Context.MODE_PRIVATE)
        realPrefs.edit().clear().commit()
        // Seed one key so there's something to wipe.
        realPrefs.edit().putString("access_token", "seed").commit()

        val commitCalls = AtomicInteger(0)
        val applyCalls = AtomicInteger(0)

        val countingPrefs =
            object : SharedPreferences by realPrefs {
                override fun edit(): SharedPreferences.Editor = CountingEditor(realPrefs.edit(), commitCalls, applyCalls)
            }

        val sessionManager = newSessionManagerWith(countingPrefs)
        sessionManager.clearSession()

        // Durability contract: commit() was called (synchronous fsync) and
        // apply() was NOT called (async, crash-unsafe) on the clearSession
        // editor. At least one commit is expected; zero applies.
        assertTrue(
            commitCalls.get() >= 1,
            "clearSession must terminate its edit chain with commit(); got $commitCalls commit / $applyCalls apply",
        )
        assertEquals(
            0,
            applyCalls.get(),
            "clearSession must not use apply() (async disk write is crash-unsafe before flag flip)",
        )
    }

    /**
     * Delegating [SharedPreferences.Editor] that tracks terminal `commit()`
     * vs `apply()` calls while forwarding every mutator to a real editor.
     */
    private class CountingEditor(
        private val delegate: SharedPreferences.Editor,
        private val commitCalls: AtomicInteger,
        private val applyCalls: AtomicInteger,
    ) : SharedPreferences.Editor {
        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = also { delegate.putString(key, value) }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = also { delegate.putStringSet(key, values) }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = also { delegate.putInt(key, value) }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = also { delegate.putLong(key, value) }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = also { delegate.putFloat(key, value) }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = also { delegate.putBoolean(key, value) }

        override fun remove(key: String?): SharedPreferences.Editor = also { delegate.remove(key) }

        override fun clear(): SharedPreferences.Editor = also { delegate.clear() }

        override fun commit(): Boolean {
            commitCalls.incrementAndGet()
            return delegate.commit()
        }

        override fun apply() {
            applyCalls.incrementAndGet()
            delegate.apply()
        }
    }
}
