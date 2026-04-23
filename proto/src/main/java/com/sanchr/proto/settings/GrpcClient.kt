package com.sanchr.proto.settings

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the SettingsService.
 * Generated stub equivalent for sanchr.settings.SettingsService.
 */
interface SettingsServiceClient {
    suspend fun getSettings(request: GetSettingsRequest): UserSettings

    suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings

    suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse

    suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings

    suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class SettingsServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : SettingsServiceClient {
    override suspend fun getSettings(request: GetSettingsRequest): UserSettings = throw NotImplementedError("Awaiting protobuf codegen")

    override suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings =
        throw NotImplementedError("Awaiting protobuf codegen")

    override suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse =
        throw NotImplementedError("Awaiting protobuf codegen")

    override suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings =
        throw NotImplementedError("Awaiting protobuf codegen")

    override suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse =
        throw NotImplementedError("Awaiting protobuf codegen")
}
