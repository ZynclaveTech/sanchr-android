package com.sanchr.core.crypto.store

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Signal Protocol cryptographic components.
 *
 * All store classes ([SanchrIdentityKeyStore], [SanchrPreKeyStore],
 * [SanchrSignedPreKeyStore], [SanchrSessionStore], [SanchrSenderKeyStore]),
 * the unified [SanchrSignalProtocolStore], and the manager classes
 * ([SignalKeyManager], [SignalSessionManager]) are annotated with
 * `@Singleton @Inject constructor` and are automatically discovered by Hilt.
 *
 * This module exists as an anchor for future bindings that may require
 * explicit `@Provides` or `@Binds` methods (e.g., binding interfaces
 * to implementations, or providing test doubles).
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoStoreModule
