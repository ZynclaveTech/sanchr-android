package com.sanchr.core.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Where the current FCM registration token comes from; Firebase in the app, a lambda in tests. */
fun interface FcmTokenSource {
    suspend fun currentToken(): String?

    companion object {
        val firebase: FcmTokenSource = FcmTokenSource { FirebaseMessaging.getInstance().token.await() }
    }
}
