package com.sanchr.core.model

/**
 * The one place that decides what to call another user.
 *
 * Precedence, matching iOS:
 *  1. the name from the device address book (what *this* user calls them),
 *  2. their phone number,
 *  3. the display name they set themselves, decrypted under their Profile
 *     Key — prefixed with `~` so the reader knows it is self-asserted rather
 *     than something they saved in their own contacts,
 *  4. [UNKNOWN].
 *
 * Server plaintext is never an input. The backend fills `display_name` with
 * [SERVER_PLACEHOLDER] for accounts that never set one and, since profile
 * fields are uploaded encrypted, has nothing better for the rest; a caller
 * that still holds such a value in an address-book slot gets it filtered.
 */
object ContactDisplayName {
    const val SERVER_PLACEHOLDER = "Sanchr User"
    const val UNKNOWN = "Unknown contact"
    const val PROFILE_NAME_PREFIX = "~"

    fun resolve(
        addressBookName: String?,
        phoneNumber: String?,
        profileName: String?,
    ): String {
        val fromAddressBook = addressBookName?.trim().orEmpty()
        if (fromAddressBook.isNotEmpty() && fromAddressBook != SERVER_PLACEHOLDER) return fromAddressBook
        val phone = phoneNumber?.trim().orEmpty()
        if (phone.isNotEmpty()) return phone
        val fromProfile = profileName?.trim().orEmpty()
        if (fromProfile.isNotEmpty()) return PROFILE_NAME_PREFIX + fromProfile
        return UNKNOWN
    }
}
