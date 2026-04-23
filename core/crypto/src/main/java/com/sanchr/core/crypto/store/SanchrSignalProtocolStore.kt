package com.sanchr.core.crypto.store

import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * Unified Signal Protocol store that delegates to purpose-specific stores.
 *
 * Implements [SignalProtocolStore] (which extends IdentityKeyStore, PreKeyStore,
 * SignedPreKeyStore, SessionStore, and KyberPreKeyStore) via Kotlin delegation,
 * and additionally implements [SenderKeyStore] for group messaging.
 *
 * All sub-stores are singletons managed by Hilt and backed by encrypted
 * persistent storage (EncryptedSharedPreferences or encrypted files).
 */
@Singleton
class SanchrSignalProtocolStore
    @Inject
    constructor(
        private val identityKeyStore: SanchrIdentityKeyStore,
        private val preKeyStore: SanchrPreKeyStore,
        private val signedPreKeyStore: SanchrSignedPreKeyStore,
        private val sessionStore: SanchrSessionStore,
        private val senderKeyStore: SanchrSenderKeyStore,
        private val kyberPreKeyStore: SanchrKyberPreKeyStore,
    ) : SignalProtocolStore,
        IdentityKeyStore by identityKeyStore,
        PreKeyStore by preKeyStore,
        SignedPreKeyStore by signedPreKeyStore,
        SessionStore by sessionStore,
        SenderKeyStore by senderKeyStore,
        KyberPreKeyStore by kyberPreKeyStore {
        /**
         * Wipes all cryptographic material across every sub-store.
         * Called during account deletion to ensure zero residual key material.
         */
        fun wipeAll() {
            identityKeyStore.wipeAll()
            preKeyStore.wipeAll()
            signedPreKeyStore.wipeAll()
            sessionStore.wipeAll()
            senderKeyStore.wipeAll()
            kyberPreKeyStore.wipeAll()
        }
    }
