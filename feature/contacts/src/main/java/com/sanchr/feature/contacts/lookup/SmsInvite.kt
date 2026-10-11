package com.sanchr.feature.contacts.lookup

/**
 * An invitation, for someone looked up by number who is not on Sanchr.
 *
 * Being told "nobody on Sanchr is using that number" was where the screen
 * stopped. The person had been found not to be here, and there was nothing to
 * do about it. iOS offers to text them; this is that.
 *
 * Nothing is sent by the app. It opens the phone's own messaging app with the
 * number and the text filled in, and the user sends it — or does not — from
 * there, at their carrier's cost and under their own name.
 */
internal object SmsInvite {
    /** The same words iOS sends. */
    const val MESSAGE =
        "Hey! I use Sanchr for private, end-to-end encrypted messaging. Join me — download it at https://sanchr.com/download"

    /** The intent extra the platform's SMS apps read the body from. */
    const val BODY_EXTRA = "sms_body"

    /**
     * The `smsto:` address for a number as it was typed, or null if there is
     * no number in it. Only digits and a leading plus are kept: the text is
     * the user's, and it goes into a URI.
     */
    fun addressFor(typedNumber: String): String? {
        val trimmed = typedNumber.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isEmpty()) return null
        return "smsto:" + (if (trimmed.startsWith("+")) "+" else "") + digits
    }
}
