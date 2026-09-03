package com.sanchr.app.navigation

import android.content.Intent

/** Where a notification tap wants the app to go, parsed from the intent extras NotificationHandler sets. */
sealed interface PendingDestination {
    val route: String

    data class Conversation(
        val id: String,
    ) : PendingDestination {
        override val route: String get() = "chats/detail/$id"
    }

    data class Call(
        val id: String,
        val action: String?,
    ) : PendingDestination {
        override val route: String get() = if (action.isNullOrEmpty()) "calls/active/$id" else "calls/active/$id?action=$action"
    }

    companion object {
        fun fromExtras(extras: Map<String, String?>): PendingDestination? {
            extras["conversationId"]?.takeIf { it.isNotEmpty() }?.let { return Conversation(it) }
            extras["call_id"]?.takeIf { it.isNotEmpty() }?.let { return Call(it, extras["call_action"]) }
            return null
        }

        fun fromIntent(intent: Intent?): PendingDestination? =
            fromExtras(
                mapOf(
                    "conversationId" to intent?.getStringExtra("conversationId"),
                    "call_id" to intent?.getStringExtra("call_id"),
                    "call_action" to intent?.getStringExtra("call_action"),
                ),
            )
    }
}
