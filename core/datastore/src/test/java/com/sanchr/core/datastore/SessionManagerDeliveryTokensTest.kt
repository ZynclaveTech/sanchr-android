package com.sanchr.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trips [SessionManager.saveDeliveryTokens]/[SessionManager.getDeliveryTokens]
 * through the real base64 codec.
 *
 * [DeliveryTokenStoreTest][com.sanchr.domain.messaging.DeliveryTokenStoreTest]
 * mocks [SessionManager] entirely, so it never exercises the actual encode
 * and decode in these two methods — including the guard against
 * `"".split(",")` returning a single empty-string element for an empty
 * pool. This is the only place that does.
 *
 * `EncryptedSharedPreferences` requires the Android Keystore, which is not
 * exposed under Robolectric, so — like [SessionManagerClearSessionTest] —
 * a plain [SharedPreferences] is injected into the private `encryptedPrefs`
 * lazy delegate via reflection, leaving the production public API untouched.
 */
@RunWith(RobolectricTestRunner::class)
class SessionManagerDeliveryTokensTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun token(fill: Int): ByteArray = ByteArray(32) { fill.toByte() }

    private fun newSessionManagerWith(prefs: SharedPreferences): SessionManager {
        val sm = SessionManager(context)
        val field = SessionManager::class.java.getDeclaredField("encryptedPrefs\$delegate")
        field.isAccessible = true
        // Replace the Lazy<SharedPreferences> with an eagerly-resolved one.
        field.set(sm, lazy { prefs })
        return sm
    }

    @Test
    fun `getDeliveryTokens returns an empty list when nothing has been saved`() {
        val prefs = context.getSharedPreferences("delivery_tokens_empty_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val sessionManager = newSessionManagerWith(prefs)

        assertEquals(emptyList(), sessionManager.getDeliveryTokens())
    }

    @Test
    fun `saveDeliveryTokens then getDeliveryTokens round-trips an empty pool`() {
        val prefs = context.getSharedPreferences("delivery_tokens_empty_roundtrip_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val sessionManager = newSessionManagerWith(prefs)

        sessionManager.saveDeliveryTokens(emptyList())

        assertEquals(emptyList(), sessionManager.getDeliveryTokens())
    }

    @Test
    fun `saveDeliveryTokens then getDeliveryTokens round-trips N tokens in order, including bytes above 0x7F`() {
        val prefs = context.getSharedPreferences("delivery_tokens_roundtrip_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val sessionManager = newSessionManagerWith(prefs)

        // 0x80 and 0xFF exercise the classic Java `byte` signedness trap:
        // as a Kotlin Byte these are negative (-128, -1), which base64
        // encode/decode must round-trip correctly regardless.
        val tokens = listOf(token(0x00), token(0x7F), token(0x80), token(0xFF), token(0x42))

        sessionManager.saveDeliveryTokens(tokens)
        val roundTripped = sessionManager.getDeliveryTokens()

        assertEquals(tokens.size, roundTripped.size)
        tokens.indices.forEach { i ->
            assertTrue(tokens[i].contentEquals(roundTripped[i]), "token at index $i must round-trip byte-for-byte, in order")
        }
    }
}
