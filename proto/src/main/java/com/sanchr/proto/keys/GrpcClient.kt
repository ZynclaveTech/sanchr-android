package com.sanchr.proto.keys

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import sanchr.keys.KeyServiceGrpcKt
import sanchr.keys.Keys

/**
 * gRPC client interface for the KeyService.
 * App-facing adapter over the generated sanchr.keys.KeyService coroutine stub.
 */
interface KeyServiceClient {

    suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse

    suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse

    suspend fun uploadOneTimePreKeys(request: UploadOneTimePreKeysRequest): PreKeyCountResponse

    suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse

    suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse
}

/**
 * Bridges the app's handwritten transport models to the generated coroutine stub.
 */
class KeyServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : KeyServiceClient {
    private val stub = KeyServiceGrpcKt.KeyServiceCoroutineStub(channel, callOptions)

    override suspend fun uploadKeyBundle(request: KeyBundle): UploadKeyBundleResponse {
        stub.uploadKeyBundle(request.toProto())
        return UploadKeyBundleResponse()
    }

    override suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse {
        return stub.getPreKeyBundle(request.toProto()).toManual()
    }

    override suspend fun uploadOneTimePreKeys(
        request: UploadOneTimePreKeysRequest,
    ): PreKeyCountResponse {
        return stub.uploadOneTimePreKeys(request.toProto()).toManual()
    }

    override suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse {
        return stub.getPreKeyCount(request.toProto()).toManual()
    }

    override suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse {
        return stub.getUserDevices(request.toProto()).toManual()
    }
}

private fun SignedPreKey.toProto(): Keys.SignedPreKey =
    Keys.SignedPreKey.newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .setSignature(ByteString.copyFrom(signature))
        .setTimestamp(timestamp)
        .build()

private fun OneTimePreKey.toProto(): Keys.OneTimePreKey =
    Keys.OneTimePreKey.newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .build()

private fun KyberPreKey.toProto(): Keys.KyberPreKey =
    Keys.KyberPreKey.newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .setSignature(ByteString.copyFrom(signature))
        .setTimestamp(timestamp)
        .build()

private fun KeyBundle.toProto(): Keys.KeyBundle =
    Keys.KeyBundle.newBuilder()
        .setIdentityPublicKey(ByteString.copyFrom(identityPublicKey))
        .apply {
            this@toProto.signedPreKey?.let { setSignedPreKey(it.toProto()) }
            addAllOneTimePreKeys(this@toProto.oneTimePreKeys.map { it.toProto() })
            if (this@toProto.registrationId > 0) {
                setRegistrationId(this@toProto.registrationId)
            }
            if (this@toProto.deviceId > 0) {
                setDeviceId(this@toProto.deviceId)
            }
            this@toProto.kyberPreKey?.let { setKyberPreKey(it.toProto()) }
        }
        .build()

private fun GetPreKeyBundleRequest.toProto(): Keys.GetPreKeyBundleRequest =
    Keys.GetPreKeyBundleRequest.newBuilder()
        .setUserId(userId)
        .setDeviceId(deviceId)
        .build()

private fun UploadOneTimePreKeysRequest.toProto(): Keys.UploadOneTimePreKeysRequest =
    Keys.UploadOneTimePreKeysRequest.newBuilder()
        .addAllKeys(keys.map(OneTimePreKey::toProto))
        .build()

private fun GetPreKeyCountRequest.toProto(): Keys.GetPreKeyCountRequest =
    Keys.GetPreKeyCountRequest.getDefaultInstance()

private fun GetUserDevicesRequest.toProto(): Keys.GetUserDevicesRequest =
    Keys.GetUserDevicesRequest.newBuilder()
        .setUserId(userId)
        .build()

private fun Keys.SignedPreKey.toManual(): SignedPreKey =
    SignedPreKey(
        keyId = keyId,
        publicKey = publicKey.toByteArray(),
        signature = signature.toByteArray(),
        timestamp = timestamp,
    )

private fun Keys.OneTimePreKey.toManual(): OneTimePreKey =
    OneTimePreKey(
        keyId = keyId,
        publicKey = publicKey.toByteArray(),
    )

private fun Keys.KyberPreKey.toManual(): KyberPreKey =
    KyberPreKey(
        keyId = keyId,
        publicKey = publicKey.toByteArray(),
        signature = signature.toByteArray(),
        timestamp = timestamp,
    )

private fun Keys.PreKeyBundleResponse.toManual(): PreKeyBundleResponse =
    PreKeyBundleResponse(
        identityPublicKey = identityPublicKey.toByteArray(),
        signedPreKey = if (hasSignedPreKey()) signedPreKey.toManual() else null,
        oneTimePreKey = if (hasOneTimePreKey()) oneTimePreKey.toManual() else null,
        registrationId = registrationId,
        deviceId = deviceId,
        kyberPreKey = if (hasKyberPreKey()) kyberPreKey.toManual() else null,
    )

private fun Keys.PreKeyCountResponse.toManual(): PreKeyCountResponse =
    PreKeyCountResponse(count = count)

private fun Keys.DeviceInfo.toManual(): DeviceInfo =
    DeviceInfo(
        deviceId = deviceId,
        platform = platform,
        supportsDeliveryAck = supportsDeliveryAck,
        keyCapable = keyCapable,
        lastActiveAt = lastActiveAt,
    )

private fun Keys.GetUserDevicesResponse.toManual(): GetUserDevicesResponse =
    GetUserDevicesResponse(
        devices = devicesList.map(Keys.DeviceInfo::toManual),
    )
