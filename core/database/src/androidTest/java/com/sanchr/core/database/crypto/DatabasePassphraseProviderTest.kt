package com.sanchr.core.database.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabasePassphraseProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val provider = DatabasePassphraseProvider(ctx)

    @After
    fun cleanup() = provider.wipe()

    @Test
    fun passphrase_is_32_bytes() {
        val pass = provider.obtainPassphrase()
        assertEquals(32, pass.size)
    }

    @Test
    fun passphrase_is_stable_across_calls() {
        val a = provider.obtainPassphrase()
        val b = provider.obtainPassphrase()
        assertTrue(a.contentEquals(b))
    }
}
