package com.sanchr.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * FCM push entry point.
 *
 * As of Phase C.2 the FCM transport carries only a content-free wake
 * signal; [onMessageReceived] never reads sender / conversation / body
 * strings and never posts a notification directly. Its sole job is to
 * enqueue [MessageDrainScheduler.enqueueDrain], which pulls pending
 * envelopes from the server, decrypts them via
 * [com.sanchr.domain.messaging.ReceiveMessageUseCase], persists the
 * decrypted rows, and acks the batch. Any user-visible notification is
 * then rendered by `NewMessageNotifier` (Phase C.4) from the local DB —
 * never from FCM data.
 *
 * [onNewToken] stays as-is: it uploads the rotated registration token via
 * [PushTokenManager].
 */
@AndroidEntryPoint
class SanchrPushService : FirebaseMessagingService() {
    @Inject lateinit var tokenManager: PushTokenManager

    @Inject lateinit var pushRouter: PushRouter

    /**
     * Service-scoped coroutine scope. [SupervisorJob] isolates failures and
     * [Dispatchers.IO] is appropriate for the token upload network call.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            tokenManager.uploadToken()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val payload = PushPayload.fromData(message.data)
        // A call wake must ring while the push's foreground-start allowance is
        // live; runBlocking keeps it inside onMessageReceived.
        runBlocking { pushRouter.route(payload) }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
