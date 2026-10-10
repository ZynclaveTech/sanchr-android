package com.sanchr.proto.keys

import com.google.protobuf.ByteString
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
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

    /**
     * The identity key the server publishes for one device, or null when it
     * publishes none. Unlike [getPreKeyBundle] this costs the device nothing:
     * no one-time pre-key is handed out.
     *
     * @throws IdentityKeyLookupUnsupported against a server from before the
     *   call existed. That is also what this default does, so a client double
     *   that does not define it behaves like such a server.
     */
    suspend fun getIdentityKey(
        userId: String,
        deviceId: Int,
    ): ByteArray? = throw IdentityKeyLookupUnsupported()
}

/** The server does not have `GetIdentityKey`; the caller has to ask another way. */
class IdentityKeyLookupUnsupported : Exception("the server does not support GetIdentityKey")

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

    override suspend fun getPreKeyBundle(request: GetPreKeyBundleRequest): PreKeyBundleResponse =
        stub.getPreKeyBundle(request.toProto()).toManual()

    override suspend fun uploadOneTimePreKeys(request: UploadOneTimePreKeysRequest): PreKeyCountResponse =
        stub.uploadOneTimePreKeys(request.toProto()).toManual()

    override suspend fun getPreKeyCount(request: GetPreKeyCountRequest): PreKeyCountResponse =
        stub.getPreKeyCount(request.toProto()).toManual()

    override suspend fun getUserDevices(request: GetUserDevicesRequest): GetUserDevicesResponse =
        stub.getUserDevices(request.toProto()).toManual()

    override suspend fun getIdentityKey(
        userId: String,
        deviceId: Int,
    ): ByteArray? =
        try {
            IdentityKeyAnswer(
                stub
                    .getIdentityKey(
                        Keys.GetIdentityKeyRequest
                            .newBuilder()
                            .setUserId(userId)
                            .setDeviceId(deviceId)
                            .build(),
                    ).identityPublicKey
                    .toByteArray()
                    .takeIf { it.isNotEmpty() },
            )
        } catch (e: StatusException) {
            answerFor(e.status.code) ?: throw e
        } catch (e: StatusRuntimeException) {
            answerFor(e.status.code) ?: throw e
        }.value

    /**
     * What a failed call still tells us. "The server publishes no such key"
     * is an answer, not a failure; "no such call" is the caller's cue to ask
     * another way. Anything else is a real failure and yields null here.
     */
    private fun answerFor(code: Status.Code): IdentityKeyAnswer? =
        when (code) {
            Status.Code.NOT_FOUND -> IdentityKeyAnswer(null)
            Status.Code.UNIMPLEMENTED -> throw IdentityKeyLookupUnsupported()
            else -> null
        }

    private class IdentityKeyAnswer(
        val value: ByteArray?,
    )
}

private fun SignedPreKey.toProto(): Keys.SignedPreKey =
    Keys.SignedPreKey
        .newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .setSignature(ByteString.copyFrom(signature))
        .setTimestamp(timestamp)
        .build()

private fun OneTimePreKey.toProto(): Keys.OneTimePreKey =
    Keys.OneTimePreKey
        .newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .build()

private fun KyberPreKey.toProto(): Keys.KyberPreKey =
    Keys.KyberPreKey
        .newBuilder()
        .setKeyId(keyId)
        .setPublicKey(ByteString.copyFrom(publicKey))
        .setSignature(ByteString.copyFrom(signature))
        .setTimestamp(timestamp)
        .build()

private fun KeyBundle.toProto(): Keys.KeyBundle =
    Keys.KeyBundle
        .newBuilder()
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
        }.build()

private fun GetPreKeyBundleRequest.toProto(): Keys.GetPreKeyBundleRequest =
    Keys.GetPreKeyBundleRequest
        .newBuilder()
        .setUserId(userId)
        .setDeviceId(deviceId)
        .build()

private fun UploadOneTimePreKeysRequest.toProto(): Keys.UploadOneTimePreKeysRequest =
    Keys.UploadOneTimePreKeysRequest
        .newBuilder()
        .addAllKeys(keys.map(OneTimePreKey::toProto))
        .build()

private fun GetPreKeyCountRequest.toProto(): Keys.GetPreKeyCountRequest = Keys.GetPreKeyCountRequest.getDefaultInstance()

private fun GetUserDevicesRequest.toProto(): Keys.GetUserDevicesRequest =
    Keys.GetUserDevicesRequest
        .newBuilder()
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

private fun Keys.PreKeyCountResponse.toManual(): PreKeyCountResponse = PreKeyCountResponse(count = count)

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
