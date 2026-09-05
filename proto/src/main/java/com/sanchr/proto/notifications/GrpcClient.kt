package com.sanchr.proto.notifications

import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.notifications.NotificationServiceGrpcKt
import sanchr.notifications.Notifications

/**
 * gRPC client interface for the NotificationService.
 *
 * RegisterPushToken and UpdateNotificationPrefs are wired in M4/M5 — the
 * FCM token upload path and global notification toggles go through here.
 * SetConversationNotificationPrefs is the per-conversation mute, sent
 * before the local flag flips (as iOS).
 */
interface NotificationServiceClient {
    suspend fun registerPushToken(request: RegisterPushTokenRequest): RegisterPushTokenResponse

    suspend fun updateNotificationPrefs(request: UpdateNotificationPrefsRequest): UpdateNotificationPrefsResponse

    suspend fun setConversationNotificationPrefs(request: SetConversationNotificationPrefsRequest)
}

class NotificationServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : NotificationServiceClient {
    private val stub by lazy { NotificationServiceGrpcKt.NotificationServiceCoroutineStub(channel, callOptions) }

    override suspend fun registerPushToken(request: RegisterPushTokenRequest): RegisterPushTokenResponse {
        stub.registerPushToken(request.toProto())
        return RegisterPushTokenResponse(success = true)
    }

    override suspend fun updateNotificationPrefs(request: UpdateNotificationPrefsRequest): UpdateNotificationPrefsResponse {
        stub.updateNotificationPrefs(request.toProto())
        return UpdateNotificationPrefsResponse(success = true)
    }

    override suspend fun setConversationNotificationPrefs(request: SetConversationNotificationPrefsRequest) {
        stub.setConversationNotificationPrefs(
            Notifications.SetConversationNotificationPrefsRequest
                .newBuilder()
                .setConversationId(request.conversationId)
                .setMuted(request.muted)
                .build(),
        )
    }
}

private fun RegisterPushTokenRequest.toProto(): Notifications.RegisterPushTokenRequest =
    Notifications.RegisterPushTokenRequest
        .newBuilder()
        .setToken(token)
        .setPlatform(platform)
        .setVoipToken(voipToken)
        .build()

private fun UpdateNotificationPrefsRequest.toProto(): Notifications.UpdateNotificationPrefsRequest =
    Notifications.UpdateNotificationPrefsRequest
        .newBuilder()
        .setMessageNotifications(messageNotifications)
        .setGroupNotifications(groupNotifications)
        .setCallNotifications(callNotifications)
        .setNotificationSound(notificationSound)
        .setVibrate(vibrate)
        .setShowPreview(showPreview)
        .build()
