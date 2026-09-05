package com.sanchr.domain.contacts

/**
 * One address-book entry as read from the device: the raw number as stored
 * and the name the user gave it. The name is what we show for a matched
 * contact (`ContactDisplayName`: address-book name first), the same way iOS
 * shows the `CNContact` name; the server never sees either.
 */
data class DeviceContact(
    val rawNumber: String,
    val name: String? = null,
)
