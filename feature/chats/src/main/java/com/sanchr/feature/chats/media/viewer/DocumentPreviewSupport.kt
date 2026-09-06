package com.sanchr.feature.chats.media.viewer

/**
 * How a given attachment can be shown.
 *
 * The distinction matters for more than layout. Anything not previewed in
 * process is handed to whatever app claims the type, along with a read grant
 * on the decrypted file — so the file leaves Sanchr's control entirely. That
 * is the current behaviour for every attachment, including PDFs, and it is
 * the reason for preferring an in-app viewer wherever one can be built.
 *
 * iOS gets this for free: QuickLook previews PDFs, Office documents, text,
 * images, audio and archives in process, and only falls back to "Open In…"
 * for what it cannot read. Android has no such system viewer, so the set
 * below is what the platform can render without shipping a document engine.
 */
enum class DocumentPreviewKind {
    /** Rendered page by page with the framework's PdfRenderer. */
    PDF,

    /** Decoded and shown directly. */
    IMAGE,

    /** Read as UTF-8 and shown as text. */
    TEXT,

    /**
     * Nothing on the platform can render it in process, so the only options
     * are another app or nothing at all.
     */
    EXTERNAL,
}

object DocumentPreviewSupport {
    private const val PDF_MIME = "application/pdf"

    /**
     * Types that are text despite not carrying a `text` prefix.
     *
     * A JSON or XML attachment arrives as `application/json` and would
     * otherwise be sent out to another app to display what is, in the end, a
     * string.
     */
    private val TEXT_LIKE =
        setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
        )

    /** Text extensions worth honouring when the sender's MIME type is useless. */
    private val TEXT_EXTENSIONS =
        setOf(
            "txt",
            "md",
            "markdown",
            "json",
            "xml",
            "yaml",
            "yml",
            "csv",
            "log",
            "kt",
            "java",
            "swift",
            "py",
            "js",
            "ts",
            "html",
            "css",
            "sh",
            "toml",
            "ini",
            "conf",
        )

    /**
     * How [mimeType] should be shown, with [fileName] as a fallback when the
     * type is missing or generic.
     *
     * A sender's MIME type is not to be trusted: files arrive as
     * `application/octet-stream` routinely, and that is exactly the case
     * where falling back to another app is worst — a decrypted document
     * leaving the app because nobody set a header.
     */
    fun kindOf(
        mimeType: String?,
        fileName: String? = null,
    ): DocumentPreviewKind {
        val mime =
            mimeType
                ?.trim()
                ?.lowercase()
                .orEmpty()
                .substringBefore(';')
        val extension = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()

        return when {
            mime == PDF_MIME || extension == "pdf" -> DocumentPreviewKind.PDF
            // Not every image/* decodes — SVG is markup — but the viewer
            // falls back on a decode failure rather than guessing here.
            mime.startsWith("image/") && mime != "image/svg+xml" -> DocumentPreviewKind.IMAGE
            mime.startsWith("text/") || mime in TEXT_LIKE -> DocumentPreviewKind.TEXT
            extension in TEXT_EXTENSIONS -> DocumentPreviewKind.TEXT
            else -> DocumentPreviewKind.EXTERNAL
        }
    }

    /** Whether the viewer can show this without handing the file to another app. */
    fun isPreviewable(
        mimeType: String?,
        fileName: String? = null,
    ): Boolean = kindOf(mimeType, fileName) != DocumentPreviewKind.EXTERNAL
}
