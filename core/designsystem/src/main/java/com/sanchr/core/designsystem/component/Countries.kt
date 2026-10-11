package com.sanchr.core.designsystem.component

import java.util.Locale

/**
 * A country someone can sign in from: its ISO region, its calling code, and
 * enough about its numbering plan to tell a number from a typo.
 *
 * [dialCode] is in E.164 leading form ("+91"); [iso] is the uppercase
 * ISO-3166 alpha-2 code, which is also what the SIM reports.
 */
data class Country(
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
    val iso: String,
    /** How long the national number may be, once the trunk zero is dropped. */
    val nationalLength: IntRange = GENERIC_NATIONAL_LENGTH,
) {
    /**
     * Countries in the North American Numbering Plan share +1 and have no
     * trunk prefix; everywhere else a leading 0 is for dialling inside the
     * country and is not part of the number.
     */
    private val usesTrunkZero: Boolean get() = dialCode != "+1"

    /**
     * What was typed, as a national number: digits only, without a leading
     * trunk zero. "07700 900123" in the United Kingdom is 7700900123.
     */
    fun nationalNumber(input: String): String {
        val digits = input.filter(Char::isDigit)
        return if (usesTrunkZero) digits.trimStart('0') else digits
    }

    fun isValid(nationalNumber: String): Boolean =
        nationalNumber.length in nationalLength && nationalNumber.all(Char::isDigit) && !nationalNumber.startsWith('0')

    /** E.164 for the server, or null if what was typed is not a number this country could have. */
    fun e164(input: String): String? = nationalNumber(input).takeIf(::isValid)?.let { dialCode + it }

    companion object {
        /** For a country whose plan is not known here: what E.164 itself allows. */
        val GENERIC_NATIONAL_LENGTH = 4..14
    }
}

/**
 * Every country and territory with a calling code.
 *
 * This was a "curated short list" of 31. Its own comment said a user
 * elsewhere "can still search / scroll" — but the search only filtered those
 * 31, and there was nowhere to type a code. Someone in Nepal, Sri Lanka or
 * Thailand could not create an account from an Android phone at all. iOS has
 * carried the full list for a while; this is the same table.
 *
 * Names come from the device, in the device's language. Sorted by name.
 */
val SanchrCountries: List<Country> by lazy {
    COUNTRY_TABLE
        .map { row ->
            Country(
                name = Locale("", row.iso).getDisplayCountry(Locale.getDefault()).ifBlank { row.iso },
                dialCode = "+" + row.callingCode,
                flagEmoji = flagOf(row.iso),
                iso = row.iso,
                nationalLength = row.minLength..row.maxLength,
            )
        }.sortedBy { it.name }
}

/** The regional-indicator pair for an ISO code, which fonts draw as its flag. */
internal fun flagOf(iso: String): String =
    iso.uppercase().map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")

private const val REGIONAL_INDICATOR_A = 0x1F1E6

private class Row(
    val iso: String,
    val callingCode: String,
    val minLength: Int,
    val maxLength: Int,
)

/** ISO region, calling code, and the shortest and longest national number. Kept in step with iOS's `PhoneCountry`. */
private val COUNTRY_TABLE: List<Row> =
    listOf(
        Row("AF", "93", 9, 9),
        Row("AL", "355", 9, 9),
        Row("DZ", "213", 9, 9),
        Row("AS", "1", 10, 10),
        Row("AD", "376", 6, 9),
        Row("AO", "244", 9, 9),
        Row("AI", "1", 10, 10),
        Row("AG", "1", 10, 10),
        Row("AR", "54", 10, 10),
        Row("AM", "374", 8, 8),
        Row("AW", "297", 7, 7),
        Row("AU", "61", 9, 9),
        Row("AT", "43", 10, 13),
        Row("AZ", "994", 9, 9),
        Row("BS", "1", 10, 10),
        Row("BH", "973", 8, 8),
        Row("BD", "880", 10, 10),
        Row("BB", "1", 10, 10),
        Row("BY", "375", 9, 9),
        Row("BE", "32", 9, 9),
        Row("BZ", "501", 7, 7),
        Row("BJ", "229", 8, 10),
        Row("BM", "1", 10, 10),
        Row("BT", "975", 8, 8),
        Row("BO", "591", 8, 8),
        Row("BA", "387", 8, 8),
        Row("BW", "267", 8, 8),
        Row("BR", "55", 10, 11),
        Row("IO", "246", 7, 7),
        Row("VG", "1", 10, 10),
        Row("BN", "673", 7, 7),
        Row("BG", "359", 9, 9),
        Row("BF", "226", 8, 8),
        Row("BI", "257", 8, 8),
        Row("KH", "855", 8, 9),
        Row("CM", "237", 9, 9),
        Row("CA", "1", 10, 10),
        Row("CV", "238", 7, 7),
        Row("KY", "1", 10, 10),
        Row("CF", "236", 8, 8),
        Row("TD", "235", 8, 8),
        Row("CL", "56", 9, 9),
        Row("CN", "86", 11, 11),
        Row("CO", "57", 10, 10),
        Row("KM", "269", 7, 7),
        Row("CG", "242", 9, 9),
        Row("CD", "243", 9, 9),
        Row("CK", "682", 5, 5),
        Row("CR", "506", 8, 8),
        Row("CI", "225", 10, 10),
        Row("HR", "385", 9, 9),
        Row("CU", "53", 8, 8),
        Row("CW", "599", 7, 8),
        Row("CY", "357", 8, 8),
        Row("CZ", "420", 9, 9),
        Row("DK", "45", 8, 8),
        Row("DJ", "253", 8, 8),
        Row("DM", "1", 10, 10),
        Row("DO", "1", 10, 10),
        Row("EC", "593", 9, 9),
        Row("EG", "20", 10, 10),
        Row("SV", "503", 8, 8),
        Row("GQ", "240", 9, 9),
        Row("ER", "291", 7, 7),
        Row("EE", "372", 7, 8),
        Row("SZ", "268", 8, 8),
        Row("ET", "251", 9, 9),
        Row("FK", "500", 5, 5),
        Row("FO", "298", 6, 6),
        Row("FJ", "679", 7, 7),
        Row("FI", "358", 6, 10),
        Row("FR", "33", 9, 9),
        Row("GF", "594", 9, 9),
        Row("PF", "689", 8, 8),
        Row("GA", "241", 7, 8),
        Row("GM", "220", 7, 7),
        Row("GE", "995", 9, 9),
        Row("DE", "49", 10, 11),
        Row("GH", "233", 9, 9),
        Row("GI", "350", 8, 8),
        Row("GR", "30", 10, 10),
        Row("GL", "299", 6, 6),
        Row("GD", "1", 10, 10),
        Row("GP", "590", 9, 9),
        Row("GU", "1", 10, 10),
        Row("GT", "502", 8, 8),
        Row("GG", "44", 10, 10),
        Row("GN", "224", 9, 9),
        Row("GW", "245", 9, 9),
        Row("GY", "592", 7, 7),
        Row("HT", "509", 8, 8),
        Row("HN", "504", 8, 8),
        Row("HK", "852", 8, 8),
        Row("HU", "36", 9, 9),
        Row("IS", "354", 7, 7),
        Row("IN", "91", 10, 10),
        Row("ID", "62", 9, 12),
        Row("IR", "98", 10, 10),
        Row("IQ", "964", 10, 10),
        Row("IE", "353", 9, 9),
        Row("IM", "44", 10, 10),
        Row("IL", "972", 9, 9),
        Row("IT", "39", 9, 10),
        Row("JM", "1", 10, 10),
        Row("JP", "81", 10, 10),
        Row("JE", "44", 10, 10),
        Row("JO", "962", 9, 9),
        Row("KZ", "7", 10, 10),
        Row("KE", "254", 9, 9),
        Row("KI", "686", 8, 8),
        Row("XK", "383", 8, 8),
        Row("KW", "965", 8, 8),
        Row("KG", "996", 9, 9),
        Row("LA", "856", 9, 10),
        Row("LV", "371", 8, 8),
        Row("LB", "961", 7, 8),
        Row("LS", "266", 8, 8),
        Row("LR", "231", 8, 9),
        Row("LY", "218", 9, 9),
        Row("LI", "423", 7, 9),
        Row("LT", "370", 8, 8),
        Row("LU", "352", 9, 9),
        Row("MO", "853", 8, 8),
        Row("MG", "261", 9, 9),
        Row("MW", "265", 9, 9),
        Row("MY", "60", 9, 10),
        Row("MV", "960", 7, 7),
        Row("ML", "223", 8, 8),
        Row("MT", "356", 8, 8),
        Row("MH", "692", 7, 7),
        Row("MQ", "596", 9, 9),
        Row("MR", "222", 8, 8),
        Row("MU", "230", 8, 8),
        Row("YT", "262", 9, 9),
        Row("MX", "52", 10, 10),
        Row("FM", "691", 7, 7),
        Row("MD", "373", 8, 8),
        Row("MC", "377", 8, 9),
        Row("MN", "976", 8, 8),
        Row("ME", "382", 8, 8),
        Row("MS", "1", 10, 10),
        Row("MA", "212", 9, 9),
        Row("MZ", "258", 9, 9),
        Row("MM", "95", 8, 10),
        Row("NA", "264", 9, 9),
        Row("NR", "674", 7, 7),
        Row("NP", "977", 10, 10),
        Row("NL", "31", 9, 9),
        Row("NC", "687", 6, 6),
        Row("NZ", "64", 8, 10),
        Row("NI", "505", 8, 8),
        Row("NE", "227", 8, 8),
        Row("NG", "234", 10, 10),
        Row("NU", "683", 4, 4),
        Row("NF", "672", 6, 6),
        Row("KP", "850", 10, 10),
        Row("MK", "389", 8, 8),
        Row("MP", "1", 10, 10),
        Row("NO", "47", 8, 8),
        Row("OM", "968", 8, 8),
        Row("PK", "92", 10, 10),
        Row("PW", "680", 7, 7),
        Row("PS", "970", 9, 9),
        Row("PA", "507", 8, 8),
        Row("PG", "675", 8, 8),
        Row("PY", "595", 9, 9),
        Row("PE", "51", 9, 9),
        Row("PH", "63", 10, 10),
        Row("PL", "48", 9, 9),
        Row("PT", "351", 9, 9),
        Row("PR", "1", 10, 10),
        Row("QA", "974", 8, 8),
        Row("RE", "262", 9, 9),
        Row("RO", "40", 9, 9),
        Row("RU", "7", 10, 10),
        Row("RW", "250", 9, 9),
        Row("BL", "590", 9, 9),
        Row("SH", "290", 4, 5),
        Row("KN", "1", 10, 10),
        Row("LC", "1", 10, 10),
        Row("MF", "590", 9, 9),
        Row("PM", "508", 6, 6),
        Row("VC", "1", 10, 10),
        Row("WS", "685", 5, 7),
        Row("SM", "378", 6, 10),
        Row("ST", "239", 7, 7),
        Row("SA", "966", 9, 9),
        Row("SN", "221", 9, 9),
        Row("RS", "381", 8, 9),
        Row("SC", "248", 7, 7),
        Row("SL", "232", 8, 8),
        Row("SG", "65", 8, 8),
        Row("SX", "1", 10, 10),
        Row("SK", "421", 9, 9),
        Row("SI", "386", 8, 8),
        Row("SB", "677", 5, 7),
        Row("SO", "252", 8, 9),
        Row("ZA", "27", 9, 9),
        Row("KR", "82", 9, 10),
        Row("SS", "211", 9, 9),
        Row("ES", "34", 9, 9),
        Row("LK", "94", 9, 9),
        Row("SD", "249", 9, 9),
        Row("SR", "597", 6, 7),
        Row("SE", "46", 7, 10),
        Row("CH", "41", 9, 9),
        Row("SY", "963", 9, 9),
        Row("TW", "886", 9, 9),
        Row("TJ", "992", 9, 9),
        Row("TZ", "255", 9, 9),
        Row("TH", "66", 9, 9),
        Row("TL", "670", 7, 8),
        Row("TG", "228", 8, 8),
        Row("TK", "690", 4, 4),
        Row("TO", "676", 5, 7),
        Row("TT", "1", 10, 10),
        Row("TN", "216", 8, 8),
        Row("TR", "90", 10, 10),
        Row("TM", "993", 8, 8),
        Row("TC", "1", 10, 10),
        Row("TV", "688", 5, 6),
        Row("UG", "256", 9, 9),
        Row("UA", "380", 9, 9),
        Row("AE", "971", 9, 9),
        Row("GB", "44", 10, 10),
        Row("US", "1", 10, 10),
        Row("UY", "598", 8, 8),
        Row("VI", "1", 10, 10),
        Row("UZ", "998", 9, 9),
        Row("VU", "678", 5, 7),
        Row("VA", "39", 9, 10),
        Row("VE", "58", 10, 10),
        Row("VN", "84", 9, 10),
        Row("WF", "681", 6, 6),
        Row("YE", "967", 9, 9),
        Row("ZM", "260", 9, 9),
        Row("ZW", "263", 9, 9),
    )
