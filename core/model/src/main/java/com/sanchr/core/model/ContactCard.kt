package com.sanchr.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `contact` content type's wire body, as iOS sends and reads it: a bare
 * `{"name":…,"phoneNumber":…}` object rather than a `MessageContent`
 * envelope. The same JSON is what gets stored as the row's content body.
 */
@Serializable
data class ContactCard(
    val name: String,
    val phoneNumber: String,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CONTENT_TYPE = "contact"
        private val json = Json { ignoreUnknownKeys = true }

        /** Null for malformed bodies or a card with neither a name nor a number. */
        fun decode(body: String): ContactCard? =
            runCatching { json.decodeFromString(serializer(), body) }
                .getOrNull()
                ?.takeIf { it.name.isNotBlank() || it.phoneNumber.isNotBlank() }
    }
}
