package com.sanchr.app.media

import android.graphics.Bitmap
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import com.sanchr.feature.chats.media.editor.EditedImageStore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

// Lives in :app, not :feature:chats, on purpose. The FileProvider and its
// file_paths entry are declared in the app manifest, so this is the only
// module where the authority resolves at all — and that manifest wiring is
// the part that breaks silently.
@RunWith(RobolectricTestRunner::class)
// A plain Application, not SanchrApp: booting the real one starts Hilt, which
// builds the database and loads SQLCipher's native library — absent on the
// JVM, so the test would die on an UnsatisfiedLinkError before reaching
// anything it means to check.
@Config(sdk = [33], application = android.app.Application::class)
class EditedImageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * Deliberately one test rather than four.
     *
     * `FileProvider` caches its resolved path strategy in a static map keyed
     * by authority, while Robolectric hands every test method a fresh cache
     * directory. Split across methods, the first to run passes and the rest
     * fail against a root that no longer exists — an artefact of the harness
     * that reads exactly like a broken store.
     */
    @Test
    fun `an edited photo comes back as something the uploader can send`() {
        // FileProvider derives the type from the extension via MimeTypeMap,
        // whose Robolectric shadow ships empty. Registering the one mapping
        // keeps the assertion meaningful: it is the .jpg extension that makes
        // this an image to the resolver.
        Shadows.shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg", "image/jpeg")

        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val uri = assertNotNull(EditedImageStore.save(context, bitmap), "the editor produced no file")

        // A file:// URI reports no type, and AttachmentPreparer would fall
        // back to application/octet-stream — the photo would arrive as a
        // nameless download rather than an image, with no error anywhere.
        assertEquals("content", uri.scheme)
        assertTrue(
            context.contentResolver.getType(uri)?.startsWith("image/") == true,
            "resolver reported ${context.contentResolver.getType(uri)}",
        )

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        assertTrue(bytes != null && bytes.isNotEmpty(), "the saved photo could not be read back")

        // Two edits in the same millisecond must not share a filename, or the
        // second silently replaces the first and the strip shows two entries
        // pointing at one photo.
        val second = assertNotNull(EditedImageStore.save(context, bitmap))
        assertTrue(uri != second, "the second edit overwrote the first")
    }
}
