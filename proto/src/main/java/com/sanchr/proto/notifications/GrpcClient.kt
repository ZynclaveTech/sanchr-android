package com.sanchr.proto.notifications

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the NotificationService.
 * Generated stub equivalent for sanchr.notifications.NotificationService.
 */
interface NotificationServiceClient {

    suspend fun registerPushToken(request: RegisterPushTokenRequest): RegisterPushTokenResponse

    suspend fun updateNotificationPrefs(
        request: UpdateNotificationPrefsRequest,
    ): UpdateNotificationPrefsResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class NotificationServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : NotificationServiceClient {

    override suspend fun registerPushToken(
        request: RegisterPushTokenRequest,
    ): RegisterPushTokenResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun updateNotificationPrefs(
        request: UpdateNotificationPrefsRequest,
    ): UpdateNotificationPrefsResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
