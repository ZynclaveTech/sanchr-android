package com.sanchr.core.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class UserId(
    val value: String,
)

@JvmInline
@Serializable
value class DeviceId(
    val value: String,
)

@JvmInline
@Serializable
value class MessageId(
    val value: String,
)

@JvmInline
@Serializable
value class ConversationId(
    val value: String,
)

@JvmInline
@Serializable
value class ThreadId(
    val value: String,
)
