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

        /**
         * Lazy re-hydration of the staged-identity blob from disk.
         *
         * This work used to live in an `init { }` block, which bit us: Hilt
         * instantiates `@Singleton` providers eagerly during
         * `SanchrApp.onCreate`, and that runs on the main thread — so the
         * `accountDao.getCurrentBlocking()` call there tripped Room's
         * `assertNotMainThread` and crashed the app on launch.
         *
         * The class contract says every public entry point is called from
         * `SignalDispatcher` (documented at the top of this file). Moving
         * the hydration into a `lazy` means it runs on that dispatcher the
         * first time any method is invoked, never on main. Correctness of
         * the rehydration logic is unchanged — just the "when" is deferred.
         *
         * `LazyThreadSafetyMode.SYNCHRONIZED` is intentional: the single
         * `SignalDispatcher` serialises calls but other dispatchers could
         * race during initializeAccount → getIdentityKeyPair paths in
         * future work; pay the small monitor-entry cost for safety.
         */
        private val stagedHydration: Unit by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            // Re-hydrate in-memory staging from disk on process restart, but
            // only if no account row has been written yet (otherwise the DB
            // is the source of truth and the staged blob is stale garbage).
            if (accountDao.getCurrentBlocking() == null) {
                val staged = stagedStore.loadStaged()
                if (staged != null) {
                    stagedIdentityKeyPair = staged.keypair
                    stagedRegistrationId = staged.registrationId
                }
            } else {
                // Account exists — any stale staged blob is dead weight.
                stagedStore.clear()
            }
            // Explicit Unit so the lazy block's inferred type is Unit, not
            // Unit? (the `?.let { }` earlier made the `if` branch's value
            // nullable even though we don't use it).
            Unit
        }

        /** Forces [stagedHydration] to evaluate. No-op after the first call. */
        private fun ensureHydrated() {
            stagedHydration
        }

        override fun getIdentityKeyPair(): IdentityKeyPair {
            ensureHydrated()
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
            ensureHydrated()
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
            ensureHydrated()
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
            ensureHydrated()
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
            ensureHydrated()
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
            ensureHydrated()
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
            val incoming = identityKey.serialize()
            val changed = existing != null && !existing.identityKey.contentEquals(incoming)

            identityDao.upsertBlocking(
                SignalIdentityEntity(
                    address = key,
                    identityKey = incoming,
                    trustLevel = TRUST_LEVEL_TRUSTED,
                    firstSeenAt = existing?.firstSeenAt ?: System.currentTimeMillis(),
                    // A manual verification vouches for one specific key. When
                    // the key changes the old comparison proves nothing, so the
                    // badge is dropped and the user has to compare again.
                    verifiedAt = if (changed) null else existing?.verifiedAt,
                    // Adopting the key on the receiving path must not erase the
                    // fact that it changed; only the user's review clears that.
                    pendingIdentityKey = if (changed) incoming else existing?.pendingIdentityKey,
                ),
            )

            return if (changed) {
                IdentityKeyStore.IdentityChange.REPLACED_EXISTING
            } else {
                IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            }
        }

        /**
         * Decides whether [identityKey] may be used for [address].
         *
         * Trust on first use: an address never seen is trusted, and an
         * unchanged key stays trusted. A *changed* key is the security-relevant
         * case, because it is exactly what a server substituting its own key
         * would produce.
         *
         * On the first sighting of a change the new key is recorded for review
         * and any verification is revoked. The two directions then diverge, as
         * on iOS:
         *
         * - Receiving returns true, so messages already sent to us still
         *   decrypt and the conversation is not silently broken. libsignal then
         *   calls [saveIdentity], which adopts the new key.
         * - Sending returns false while the change is unreviewed, so encryption
         *   fails closed rather than handing plaintext to whoever supplied the
         *   new key.
         *
         * Before this, both directions were refused, which left a contact who
         * merely reinstalled permanently undecryptable with no way back.
         */
        override fun isTrustedIdentity(
            address: SignalProtocolAddress,
            identityKey: IdentityKey,
            direction: IdentityKeyStore.Direction,
        ): Boolean {
            val key = address.toRoomKey()
            val existing = identityDao.getBlocking(key) ?: return true // TOFU
            val incoming = identityKey.serialize()
            val changed = !existing.identityKey.contentEquals(incoming)

            if (changed && existing.pendingIdentityKey == null) {
                identityDao.upsertBlocking(existing.copy(pendingIdentityKey = incoming, verifiedAt = null))
            }

            val reviewPending = changed || existing.pendingIdentityKey != null
            if (!reviewPending) return true
            return direction == IdentityKeyStore.Direction.RECEIVING
        }

        override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
            val existing = identityDao.getBlocking(address.toRoomKey()) ?: return null
            return IdentityKey(existing.identityKey, 0)
        }

        /** Wipes all identity material. Called on account deletion. */
        fun wipeAll() {
            // No ensureHydrated(): we're about to null out staging and wipe
            // disk-staged + account rows anyway. Hydrating first would read
            // from disk just to overwrite it a line later.
            stagedIdentityKeyPair = null
            stagedRegistrationId = null
            stagedStore.clear()
            identityDao.deleteAllBlocking()
            accountDao.deleteAllBlocking()
        }

        // ------------------------------------------------------------------
        // Manual safety-number verification
        // ------------------------------------------------------------------

        /**
         * Records that the local user compared safety numbers with [address]
         * and they matched. No-op when the identity is unknown, so a caller
         * cannot mark a contact verified before a session exists.
         */
        fun markVerified(
            address: SignalProtocolAddress,
            atMillis: Long = System.currentTimeMillis(),
        ) {
            identityDao.setVerifiedAtBlocking(address.toRoomKey(), atMillis)
            // Comparing the new safety number is a strictly stronger review
            // than merely acknowledging the change, so it clears the review too.
            identityDao.setPendingIdentityKeyBlocking(address.toRoomKey(), null)
        }

        /**
         * Whether [userId] has a key change the local user has not reviewed.
         * While true, sending to them fails closed.
         */
        fun hasPendingIdentityChange(userId: String): Boolean =
            identityDao.getForUserBlocking(userId.likePrefix()).any {
                it.pendingIdentityKey !=
                    null
            }

        /**
         * Records that the user reviewed the change and chose to continue,
         * unblocking sending.
         *
         * Deliberately does not mark the identity verified: acknowledging a
         * change is weaker than comparing safety numbers, and conflating them
         * would hand out a verified badge nobody earned.
         */
        fun acceptIdentityChange(userId: String) {
            identityDao.getForUserBlocking(userId.likePrefix()).forEach { row ->
                if (row.pendingIdentityKey != null) identityDao.setPendingIdentityKeyBlocking(row.address, null)
            }
        }

        /** Revokes a manual verification, e.g. after a scan that did not match. */
        fun clearVerified(address: SignalProtocolAddress) {
            identityDao.setVerifiedAtBlocking(address.toRoomKey(), null)
        }

        /** When [address] was verified, or null if it never was. */
        fun verifiedAtMillis(address: SignalProtocolAddress): Long? = identityDao.getBlocking(address.toRoomKey())?.verifiedAt

        /**
         * The LIKE pattern matching every device row for one user id.
         *
         * Room keys are `"<userId>.<deviceId>"`, and a user id may itself hold
         * `%` or `_`, which LIKE would otherwise read as wildcards — so they
         * are escaped and the query declares the escape character.
         */
        private fun String.likePrefix(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + ".%"

        private companion object {
            const val TRUST_LEVEL_TRUSTED = 1
        }
    }
