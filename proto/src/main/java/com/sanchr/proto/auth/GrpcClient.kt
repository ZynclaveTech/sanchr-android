package com.sanchr.proto.auth

import io.grpc.CallOptions
import io.grpc.Channel

/**
 * gRPC client interface for the AuthService.
 * Generated stub equivalent for sanchr.auth.AuthService.
 */
interface AuthServiceClient {

    suspend fun register(request: RegisterRequest): AuthResponse

    suspend fun verifyOtp(request: VerifyOTPRequest): AuthResponse

    suspend fun login(request: LoginRequest): AuthResponse

    suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse

    suspend fun logout(request: LogoutRequest): LogoutResponse
}

/**
 * Implementation shell that will delegate to the actual gRPC-generated stubs
 * once protobuf-gradle-plugin codegen runs.
 */
class AuthServiceGrpcClient(
    private val channel: Channel,
    private val callOptions: CallOptions = CallOptions.DEFAULT,
) : AuthServiceClient {

    override suspend fun register(request: RegisterRequest): AuthResponse {
        // TODO: Delegate to generated AuthServiceGrpcKt.AuthServiceCoroutineStub
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun verifyOtp(request: VerifyOTPRequest): AuthResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun login(request: LoginRequest): AuthResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }

    override suspend fun logout(request: LogoutRequest): LogoutResponse {
        throw NotImplementedError("Awaiting protobuf codegen")
    }
}
