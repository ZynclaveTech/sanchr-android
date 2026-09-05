package com.sanchr.domain.contacts

/** One entry of the server's blocked list, named the way the rest of the app names the person. */
data class BlockedContact(
    val userId: String,
    val displayName: String,
)
