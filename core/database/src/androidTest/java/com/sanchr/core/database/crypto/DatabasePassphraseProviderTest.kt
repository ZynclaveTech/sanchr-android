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
        val size = provider.withPassphrase { it.size }
        assertEquals(32, size)
    }

    @Test
    fun passphrase_is_stable_across_calls() {
        val a = provider.withPassphrase { it.copyOf() }
        val b = provider.withPassphrase { it.copyOf() }
        try {
            assertTrue(a.contentEquals(b))
        } finally {
            a.fill(0)
            b.fill(0)
        }
    }

    @Test
    fun passphrase_buffer_is_zeroed_after_block_returns() {
        lateinit var leaked: ByteArray
        provider.withPassphrase { bytes ->
            // Intentionally escape the reference to verify the provider
            // zero-fills it after the block returns.
            leaked = bytes
        }
        assertTrue(leaked.all { it == 0.toByte() }, "passphrase buffer must be zeroed on exit")
    }
}
