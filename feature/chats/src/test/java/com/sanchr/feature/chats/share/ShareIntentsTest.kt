package com.sanchr.feature.chats.share

import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reads real Intents, not a stand-in. The extras go through actual
 * parcelling and the two `EXTRA_STREAM` shapes differ between the single
 * and multiple actions, which is precisely where a share silently arrives
 * empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ShareIntentsTest {
    @Test
    fun `a shared link arrives as text`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://example.com/a")
            }

        assertEquals(SharedContent.Text("https://example.com/a"), intent.toSharedContent())
    }

    @Test
    fun `a single shared image arrives as one attachment`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/external/images/1"))
            }

        assertEquals(
            SharedContent.Attachments(listOf("content://media/external/images/1")),
            intent.toSharedContent(),
        )
    }

    @Test
    fun `several shared images arrive in order`() {
        val uris = arrayListOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }

        assertEquals(
            SharedContent.Attachments(listOf("content://media/1", "content://media/2")),
            intent.toSharedContent(),
        )
    }

    @Test
    fun `text sent with an image becomes the caption`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://media/9"))
                putExtra(Intent.EXTRA_TEXT, "on the beach")
            }

        assertEquals(
            SharedContent.Attachments(listOf("content://media/9"), caption = "on the beach"),
            intent.toSharedContent(),
        )
    }

    @Test
    fun `an intent that is not a share is ignored`() {
        assertNull(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://sanchr.com/u/1") }.toSharedContent())
        assertNull(Intent(Intent.ACTION_MAIN).toSharedContent())
    }

    @Test
    fun `a send carrying nothing usable parses to nothing`() {
        assertNull(Intent(Intent.ACTION_SEND).apply { type = "text/plain" }.toSharedContent())
    }
}
