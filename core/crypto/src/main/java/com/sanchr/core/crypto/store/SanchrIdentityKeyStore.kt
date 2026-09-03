package com.sanchr.core.crypto.store

import com.sanchr.core.crypto.StagedIdentityStore
import com.sanchr.core.database.dao.AccountDao
import com.sanchr.core.database.dao.SignalIdentityDao
import com.sanchr.core.database.entity.AccountEntity
import com.sanchr.core.database.entity.SignalIdentityEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore

/**
 * Room-backed identity key store.
 *
 * The local identity keypair and registration id live in the single-row
 * `accounts` table (per spec §4 — keeps the identity private key co-located
 * with user-binding fields inside the SQLCipher-encrypted DB).
 *
 * Peer identities live in `signal_identities`, keyed by `"name.deviceId"`.
 *
 * Because libsignal's [IdentityKeyStore] interface is synchronous, DAO calls
 * use blocking Room variants. These are safe when invoked from the single-
 * threaded `SignalDispatcher` configured by [DispatcherProvider].
 *
 * Trust model: trust-on-first-use (TOFU). Unknown peers are trusted on
 * first contact; a subsequent key change returns `REPLACED_EXISTING` from
 * [saveIdentity] so higher-level UI can raise a safety-number warning.
 */
@Singleton
class SanchrIdentityKeyStore
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val identityDao: SignalIdentityDao,
        private val stagedStore: StagedIdentityStore,
    ) : IdentityKeyStore {
        /**
         * In-memory staging for keys generated before the account row exists
         * (i.e. during the early part of registration, before the server
         * assigns the final `userId`). Once [initializeAccount] is called
         * these values are flushed to the DB.
         *
         * Backed by [StagedIdentityStore] on disk so a process crash between
         * staging and [initializeAccount] does not silently forget the
         * generated identity keypair.
         */
        @Volatile
        private var stagedIdentityKeyPair: IdentityKeyPair? = null

        @Volatile
        private var stagedRegistrationId: Int? = null

        init {
            // Re-hydrate in-memory staging from disk on process restart, but
            // only if no account row has been written yet (otherwise the DB
            // is the source of truth and the staged blob is stale garbage).
            if (accountDao.getCurrentBlocking() == null) {
                stagedStore.loadStaged()?.let { staged ->
                    stagedIdentityKeyPair = staged.keypair
                    stagedRegistrationId = staged.registrationId
                }
            } else {
                // Account exists — any stale staged blob is dead weight.
                stagedStore.clear()
            }
        }

        override fun getIdentityKeyPair(): IdentityKeyPair {
            stagedIdentityKeyPair?.let { return it }
            val account =
                accountDao.getCurrentBlocking()
                    ?: throw IllegalStateException("Account not initialized — identity key pair missing")
            val bytes =
                account.identityPrivateKey
                    ?: throw IllegalStateException("Account row present but identity_private_key is null")
            return IdentityKeyPair(bytes)
        }

        override fun getLocalRegistrationId(): Int {
            stagedRegistrationId?.let { return it }
            val account =
                accountDao.getCurrentBlocking()
                    ?: throw IllegalStateException("Account not initialized — registration id missing")
            return account.registrationId
        }

        /**
         * Persists (or stages) the identity key pair generated at registration.
         *
         * If an account row already exists, the key is written through
         * immediately. Otherwise it is held in memory until
         * [initializeAccount] flushes it alongside the rest of the account
         * identifiers.
         */
        fun storeIdentityKeyPair(identityKeyPair: IdentityKeyPair) {
            val existing = accountDao.getCurrentBlocking()
            if (existing != null) {
                accountDao.upsertBlocking(
                    existing.copy(identityPrivateKey = identityKeyPair.serialize()),
                )
                stagedIdentityKeyPair = null
                // Staged-blob is dead once the DB has the row.
                stagedStore.clear()
            } else {
                stagedIdentityKeyPair = identityKeyPair
                // Mirror to disk so a crash before initializeAccount doesn't
                // lose the keypair. registrationId may be absent here — if so
                // persist a placeholder (0) and overwrite on next call.
                stagedStore.stage(identityKeyPair, stagedRegistrationId ?: 0)
            }
        }

        /**
         * Persists (or stages) the local registration id generated at
         * registration — see [storeIdentityKeyPair].
         */
        fun storeLocalRegistrationId(registrationId: Int) {
            val existing = accountDao.getCurrentBlocking()
            if (existing != null) {
                accountDao.upsertBlocking(existing.copy(registrationId = registrationId))
                stagedRegistrationId = null
                stagedStore.clear()
            } else {
                stagedRegistrationId = registrationId
                // Only mirror to disk if we already have an identity keypair —
                // registrationId without a keypair is useless.
                stagedIdentityKeyPair?.let { kp ->
                    stagedStore.stage(kp, registrationId)
                }
            }
        }

        /**
         * Creates or replaces the `accounts` row with the account identifiers
         * assigned by the server. Must be called once the registration RPC
         * completes; flushes any staged identity key pair / registration id.
         */
        fun initializeAccount(
            userId: String,
            deviceId: String,
            phoneE164: String,
        ) {
            val existing = accountDao.getCurrentBlocking()
            val staged = stagedIdentityKeyPair
            val stagedReg = stagedRegistrationId

            val identityPrivate =
                staged?.serialize()
                    ?: existing?.identityPrivateKey
                    ?: throw IllegalStateException(
                        "initializeAccount called before storeIdentityKeyPair",
                    )
            val registrationId =
                stagedReg
                    ?: existing?.registrationId
                    ?: throw IllegalStateException(
                        "initializeAccount called before storeLocalRegistrationId",
                    )

            accountDao.upsertBlocking(
                AccountEntity(
                    userId = userId,
                    deviceId = deviceId,
                    phoneE164 = phoneE164,
                    registrationId = registrationId,
                    identityPrivateKey = identityPrivate,
                ),
            )
            stagedIdentityKeyPair = null
            stagedRegistrationId = null
            stagedStore.clear()
        }

        /**
         * Returns the local user's UUID, parsed from `accounts.userId`.
         *
         * Sealed-sender requires the sender's UUID (not the opaque string userId)
         * because libsignal's `SealedSessionCipher` constructor takes a `UUID`.
         * Account creation guarantees userIds are UUID strings (matches iOS
         * `ServiceId.parseFromStringLenient` pattern).
         *
         * @throws IllegalStateException if no account row exists.
         * @throws IllegalArgumentException if `userId` is not a valid UUID.
         */
        fun getLocalUserUuid(): UUID {
            val account =
                accountDao.getCurrentBlocking()
                    ?: throw IllegalStateException("Account not initialized — local UUID unavailable")
            return UUID.fromString(account.userId)
        }

        /**
         * Returns the local device id as an int. `AccountEntity.deviceId` is a
         * string (to match server representation); libsignal wants an int.
         *
         * @throws IllegalStateException if no account row exists.
         * @throws NumberFormatException if `deviceId` is not parseable.
         */
        fun getLocalDeviceId(): Int {
            val account =
                accountDao.getCurrentBlocking()
                    ?: throw IllegalStateException("Account not initialized — device id unavailable")
            return account.deviceId.toInt()
        }

        /** Returns true once the identity key pair has been generated. */
        fun hasIdentityKeyPair(): Boolean {
            if (stagedIdentityKeyPair != null) return true
            return accountDao.getCurrentBlocking()?.identityPrivateKey != null
        }

        // ------------------------------------------------------------------
        // Peer identities
        // ------------------------------------------------------------------

        override fun saveIdentity(
            address: SignalProtocolAddress,
            identityKey: IdentityKey,
        ): IdentityKeyStore.IdentityChange {
            val key = address.toRoomKey()
            val existing = identityDao.getBlocking(key)
            val newBytes = identityKey.serialize()
            val changed = existing != null && !existing.identityKey.contentEquals(newBytes)

            identityDao.upsertBlocking(
                SignalIdentityEntity(
                    address = key,
                    identityKey = newBytes,
                    trustLevel = TRUST_LEVEL_TRUSTED,
                    firstSeenAt = existing?.firstSeenAt ?: System.currentTimeMillis(),
                ),
            )

            return if (changed) {
                IdentityKeyStore.IdentityChange.REPLACED_EXISTING
            } else {
                IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            }
        }

        override fun isTrustedIdentity(
            address: SignalProtocolAddress,
            identityKey: IdentityKey,
            direction: IdentityKeyStore.Direction,
        ): Boolean {
            val existing = identityDao.getBlocking(address.toRoomKey()) ?: return true // TOFU
            return existing.identityKey.contentEquals(identityKey.serialize())
        }

        override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
            val existing = identityDao.getBlocking(address.toRoomKey()) ?: return null
            return IdentityKey(existing.identityKey, 0)
        }

        /** Wipes all identity material. Called on account deletion. */
        fun wipeAll() {
            stagedIdentityKeyPair = null
            stagedRegistrationId = null
            stagedStore.clear()
            identityDao.deleteAllBlocking()
            accountDao.deleteAllBlocking()
        }

        private companion object {
            const val TRUST_LEVEL_TRUSTED = 1
        }
    }
