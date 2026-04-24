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
 * once the settings sync feature lands.
 */
class SettingsServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : SettingsServiceClient {
    // TODO(M6+): wire to SettingsServiceGrpcKt.SettingsServiceCoroutineStub when settings sync lands
    override suspend fun getSettings(request: GetSettingsRequest): UserSettings =
        throw NotImplementedError("Awaiting settings sync feature in M6+")

    // TODO(M6+): wire to SettingsServiceGrpcKt.SettingsServiceCoroutineStub when settings sync lands
    override suspend fun updateSettings(request: UpdateSettingsRequest): UserSettings =
        throw NotImplementedError("Awaiting settings sync feature in M6+")

    // TODO(M6+): wire to SettingsServiceGrpcKt.SettingsServiceCoroutineStub when settings sync lands
    override suspend fun updateProfile(request: UpdateProfileRequest): ProfileResponse =
        throw NotImplementedError("Awaiting settings sync feature in M6+")

    // TODO(M6+): wire to SettingsServiceGrpcKt.SettingsServiceCoroutineStub when settings sync lands
    override suspend fun toggleSanchrMode(request: ToggleSanchrModeRequest): UserSettings =
        throw NotImplementedError("Awaiting settings sync feature in M6+")

    // TODO(M6+): wire to SettingsServiceGrpcKt.SettingsServiceCoroutineStub when settings sync lands
    override suspend fun getStorageUsage(request: GetStorageUsageRequest): StorageUsageResponse =
        throw NotImplementedError("Awaiting settings sync feature in M6+")
}
