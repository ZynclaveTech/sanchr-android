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
    private val LONG_B64 = Regex("""[A-Za-z0-9+/=]{24,}""")

    /**
     * [text] with anything that could identify a person or a message
     * replaced.
     *
     * Order matters: phone numbers and emails go first, since a path or a
     * base64 run could otherwise swallow them and leave a partial number
     * behind.
     */
    fun redact(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace(EMAIL, "[email]")
            .replace(PHONE, "[phone]")
            .replace(CONTENT_URI, "[uri]")
            .replace(LONG_B64, "[data]")
            .replace(FILE_PATH, "[path]")
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
