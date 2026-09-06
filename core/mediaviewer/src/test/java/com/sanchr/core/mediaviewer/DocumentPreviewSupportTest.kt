package com.sanchr.core.mediaviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which attachments stay inside the app.
 *
 * Every EXTERNAL answer means a decrypted file handed to another app with a
 * read grant, so the cost of getting this wrong is not a worse layout — it is
 * a document leaving Sanchr because a header was missing.
 */
class DocumentPreviewSupportTest {
    @Test
    fun `a pdf is rendered in app`() {
        assertEquals(DocumentPreviewKind.PDF, DocumentPreviewSupport.kindOf("application/pdf"))
    }

    @Test
    fun `a pdf is still recognised when the sender declares nothing useful`() {
        // Files routinely arrive as octet-stream. Trusting that alone would
        // send the most common document type out to another app.
        assertEquals(
            DocumentPreviewKind.PDF,
            DocumentPreviewSupport.kindOf("application/octet-stream", "statement.pdf"),
        )
    }

    @Test
    fun `a mime type carrying a charset is still matched`() {
        assertEquals(DocumentPreviewKind.TEXT, DocumentPreviewSupport.kindOf("text/plain; charset=utf-8"))
    }

    @Test
    fun `case and padding in the mime type do not matter`() {
        assertEquals(DocumentPreviewKind.PDF, DocumentPreviewSupport.kindOf("  APPLICATION/PDF  "))
    }

    @Test
    fun `images are shown directly`() {
        for (mime in listOf("image/jpeg", "image/png", "image/webp", "image/heic")) {
            assertEquals(DocumentPreviewKind.IMAGE, DocumentPreviewSupport.kindOf(mime), mime)
        }
    }

    /** SVG is markup, and the bitmap decoder returns null for it. */
    @Test
    fun `svg is not treated as a decodable image`() {
        assertEquals(DocumentPreviewKind.EXTERNAL, DocumentPreviewSupport.kindOf("image/svg+xml"))
    }

    @Test
    fun `structured text is read as text rather than sent away`() {
        for (mime in listOf("application/json", "application/xml", "text/csv", "text/markdown")) {
            assertEquals(DocumentPreviewKind.TEXT, DocumentPreviewSupport.kindOf(mime), mime)
        }
    }

    @Test
    fun `a source file is read as text by its extension`() {
        assertEquals(DocumentPreviewKind.TEXT, DocumentPreviewSupport.kindOf(null, "Build.kt"))
        assertEquals(DocumentPreviewKind.TEXT, DocumentPreviewSupport.kindOf("application/octet-stream", "notes.md"))
    }

    @Test
    fun `an office document has no in-app renderer and says so`() {
        // Honest rather than convenient: nothing on the platform reads these,
        // and pretending otherwise would show an empty viewer.
        assertEquals(
            DocumentPreviewKind.EXTERNAL,
            DocumentPreviewSupport.kindOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "contract.docx",
            ),
        )
        assertFalse(DocumentPreviewSupport.isPreviewable("application/zip", "archive.zip"))
    }

    @Test
    fun `a missing type and a missing name fall back to another app`() {
        assertEquals(DocumentPreviewKind.EXTERNAL, DocumentPreviewSupport.kindOf(null, null))
        assertEquals(DocumentPreviewKind.EXTERNAL, DocumentPreviewSupport.kindOf("", ""))
    }

    @Test
    fun `a name with no extension does not match a text type by accident`() {
        assertEquals(DocumentPreviewKind.EXTERNAL, DocumentPreviewSupport.kindOf(null, "README"))
    }

    @Test
    fun `previewable covers everything the viewer can draw`() {
        assertTrue(DocumentPreviewSupport.isPreviewable("application/pdf"))
        assertTrue(DocumentPreviewSupport.isPreviewable("image/png"))
        assertTrue(DocumentPreviewSupport.isPreviewable("text/plain"))
    }
}
