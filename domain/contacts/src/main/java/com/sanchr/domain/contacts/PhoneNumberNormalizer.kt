package com.sanchr.domain.contacts

/**
 * Turns address-book phone strings into E.164 candidates for discovery.
 *
 * Discovery matches byte-for-byte against the number a user registered with —
 * the server does no normalisation on either side — so a local-format entry
 * like `98765 43210` can only match if it becomes `+919876543210` here. The
 * country is inferred from the signed-in user's own E.164, exactly as iOS's
 * `ContactDataSource` does: most address-book entries are in the owner's own
 * country, and that is the only signal available offline.
 *
 * Deliberately not a full libphonenumber: the rules below cover the forms
 * people actually store (`+…`, `00…`, trunk-zero national, bare national,
 * and the national-with-country-code form common in India), and a number
 * that fits none of them yields no candidate rather than a guess.
 */
object PhoneNumberNormalizer {
    /**
     * @param raw the string as stored in the address book.
     * @param ownE164 the signed-in user's number in E.164, or null if unknown,
     *   in which case only numbers already in international form qualify.
     * @return zero, one or two E.164 candidates. Two when the raw string is
     *   already international *and* also parses as a national number — the
     *   caller submits both, as iOS does; the server will only know one.
     */
    fun candidates(
        raw: String,
        ownE164: String?,
    ): List<String> {
        val stripped = raw.filter { it.isDigit() || it == '+' }
        if (stripped.isEmpty()) return emptyList()

        val international = stripped.toInternationalOrNull()
        val own = ownE164?.takeIf { it.startsWith("+") }?.let { it.filter(Char::isDigit) }
        val national = own?.let { toNational(stripped, it) }

        return listOfNotNull(international, national).distinct()
    }

    /** `+CC…` as-is; `00CC…` → `+CC…`. Requires a plausible length. */
    private fun String.toInternationalOrNull(): String? {
        val digits =
            when {
                startsWith("+") -> substring(1)
                startsWith("00") -> substring(2)
                else -> return null
            }
        if (digits.any { !it.isDigit() } || digits.length !in MIN_E164_DIGITS..MAX_E164_DIGITS) return null
        return "+$digits"
    }

    /**
     * Interpret [stripped] as a national number in the owner's country.
     * [ownDigits] is the owner's E.164 without the `+`.
     */
    private fun toNational(
        stripped: String,
        ownDigits: String,
    ): String? {
        if (stripped.startsWith("+")) return null
        val cc = callingCodeOf(ownDigits) ?: return null
        val nationalLength = ownDigits.length - cc.length
        if (nationalLength <= 0) return null

        val digits = stripped.filter(Char::isDigit)
        val body =
            when {
                // Trunk prefix: 0 + national number (UK, IN, most of Europe).
                digits.startsWith("0") && digits.length == nationalLength + 1 -> digits.substring(1)
                // Bare national number.
                digits.length == nationalLength -> digits
                // Country code written without '+' (e.g. "919876543210" in India).
                digits.startsWith(cc) && digits.length == cc.length + nationalLength -> digits.substring(cc.length)
                else -> return null
            }
        return "+$cc$body"
    }

    /**
     * The ITU calling code at the start of an E.164 digit string. Codes are 1,
     * 2 or 3 digits; the two-digit set is closed, and everything not in the
     * one- or two-digit sets is three digits.
     */
    internal fun callingCodeOf(e164Digits: String): String? {
        if (e164Digits.isEmpty()) return null
        val one = e164Digits.substring(0, 1)
        if (one in ONE_DIGIT_CODES) return one
        if (e164Digits.length < 2) return null
        val two = e164Digits.substring(0, 2)
        if (two in TWO_DIGIT_CODES) return two
        if (e164Digits.length < 3) return null
        return e164Digits.substring(0, 3)
    }

    private val ONE_DIGIT_CODES = setOf("1", "7")
    private val TWO_DIGIT_CODES =
        setOf(
            "20",
            "27",
            "30",
            "31",
            "32",
            "33",
            "34",
            "36",
            "39",
            "40",
            "41",
            "43",
            "44",
            "45",
            "46",
            "47",
            "48",
            "49",
            "51",
            "52",
            "53",
            "54",
            "55",
            "56",
            "57",
            "58",
            "60",
            "61",
            "62",
            "63",
            "64",
            "65",
            "66",
            "81",
            "82",
            "84",
            "86",
            "90",
            "91",
            "92",
            "93",
            "94",
            "95",
            "98",
        )

    private const val MIN_E164_DIGITS = 7
    private const val MAX_E164_DIGITS = 15
}
