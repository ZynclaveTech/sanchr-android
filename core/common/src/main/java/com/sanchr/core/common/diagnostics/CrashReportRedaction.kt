package com.sanchr.core.common.diagnostics

/**
 * What a crash report is allowed to say.
 *
 * A stack trace from a messenger can carry a phone number, a message body
 * or a media path in an exception message, and those must never reach a
 * third party. Class and method names are what makes a report useful; the
 * free text almost never is.
 */
object CrashReportRedaction {
    private val PHONE = Regex("""\+?\d[\d ()-]{7,}\d""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val CONTENT_URI = Regex("""content://\S+""")
    private val FILE_PATH = Regex("""(/[\w.-]+){2,}""")

    // `=` only ever pads the end of base64. Including it at the front let
    // this rule swallow a preceding "key=" and report the label as data.
    private val LONG_B64 = Regex("""[A-Za-z0-9+/]{24,}={0,2}""")

    /**
     * [text] with anything that could identify a person or a message
     * replaced.
     *
     * Order matters. Phone numbers and emails go first, since a path or a
     * base64 run could otherwise swallow them and leave a partial number
     * behind. Paths then precede base64, whose character class contains `/`
     * and so matches any sufficiently long path.
     */
    fun redact(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(EMAIL, "[email]")
            .replace(PHONE, "[phone]")
            .replace(CONTENT_URI, "[uri]")
            // Paths before base64: `/` is a base64 character, so the base64
            // rule matches any path long enough and reports it as [data].
            // Nothing leaks — it over-redacts — but a report in which every
            // path reads [data] is markedly harder to act on, which defeats
            // the point of keeping the non-identifying half.
            .replace(FILE_PATH, "[path]")
            .replace(LONG_B64, "[data]")
    }

    /**
     * A one-line label for a crash: the exception's type and where it was
     * thrown, with no free text at all.
     *
     * Preferred over [redact] where a summary suffices, because withholding
     * the message entirely cannot leak by an unanticipated shape.
     */
    fun summarise(throwable: Throwable): String {
        val frame = throwable.stackTrace.firstOrNull()
        val where = frame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: "unknown"
        return "${throwable.javaClass.name} at $where"
    }
}
