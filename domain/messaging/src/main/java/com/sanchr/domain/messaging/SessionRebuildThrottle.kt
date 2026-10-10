package com.sanchr.domain.messaging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a session with a sender's device should be rebuilt now.
 *
 * A message that cannot be decrypted for want of a session is answered by
 * starting a new one with its sender, so that the next thing sent to them
 * tells them to start over too. One rebuild does that. But the rebuild was
 * launched for every such message, with nothing to say one was already under
 * way or had just been done:
 *
 *  - Ten undecryptable messages arriving together launched ten, each fetching
 *    a bundle and so each taking one of the sender's one-time pre-keys, and
 *    each replacing the session the one before it had just made.
 *  - The message is left on the server to be delivered again, so the same
 *    thing happened on every sync for as long as it stayed undecryptable.
 *
 * A contact with one stuck message could have their one-time pre-keys drained
 * by the device they were talking to. This allows one rebuild per device at a
 * time, and not again until [cooldownMillis] has passed.
 *
 * When a rebuild was last started is kept in a [SessionRebuildLog], so the
 * answer survives the process being killed. It has to: a phone is woken by a
 * push for each message and the process rarely lives long, so a record held
 * in memory was forgotten between one undecryptable message and the next —
 * the very case this exists for.
 */
@Singleton
class SessionRebuildThrottle(
    private val log: SessionRebuildLog = InMemorySessionRebuildLog(),
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(log: SharedPreferencesSessionRebuildLog) : this(log = log, cooldownMillis = DEFAULT_COOLDOWN_MILLIS)

    private val inFlight = HashSet<String>()

    /**
     * Claims the rebuild for this device if one is due. A caller given true
     * must call [finished] when its attempt ends, however it ends.
     */
    @Synchronized
    fun tryStart(
        userId: String,
        deviceId: Int,
    ): Boolean {
        val key = "$userId.$deviceId"
        if (key in inFlight) return false
        val current = now()
        if (isCoolingDown(log.lastStarted(key), current)) return false
        inFlight += key
        log.record(key, current, forgetBefore = current - cooldownMillis)
        return true
    }

    @Synchronized
    fun finished(
        userId: String,
        deviceId: Int,
    ) {
        inFlight -= "$userId.$deviceId"
    }

    /**
     * Forgets everything, for when the account is removed from this phone.
     * The next account to use it owes nothing to who the last one talked to.
     */
    @Synchronized
    fun clear() {
        inFlight.clear()
        log.clear()
    }

    /**
     * A start in the future is a clock that has since been set back. It is
     * not honoured: taken at face value it would hold the rebuild off for
     * however far back the clock went.
     */
    private fun isCoolingDown(
        lastStarted: Long?,
        current: Long,
    ): Boolean = lastStarted != null && current - lastStarted in 0 until cooldownMillis

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 10 * 60 * 1000L
    }
}

/** When a rebuild with each sender device was last started. */
interface SessionRebuildLog {
    fun lastStarted(key: String): Long?

    /** Records a start, and drops every entry older than [forgetBefore]. */
    fun record(
        key: String,
        startedAt: Long,
        forgetBefore: Long,
    )

    fun clear()
}

class InMemorySessionRebuildLog : SessionRebuildLog {
    private val entries = HashMap<String, Long>()

    override fun lastStarted(key: String): Long? = entries[key]

    override fun record(
        key: String,
        startedAt: Long,
        forgetBefore: Long,
    ) {
        entries.values.removeAll { it < forgetBefore }
        entries[key] = startedAt
    }

    override fun clear() = entries.clear()
}

/**
 * The log on disk.
 *
 * Which contacts' messages recently failed to decrypt is not something to
 * leave lying about in the clear, even in the app's own storage: it is a
 * small piece of who this user talks to. So an entry is not stored under the
 * sender's id but under a hash of it, salted with a value that never leaves
 * this file, and an entry is dropped as soon as its cooldown is over — most
 * of the time the file holds nothing.
 */
@Singleton
class SharedPreferencesSessionRebuildLog
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SessionRebuildLog {
        private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        override fun lastStarted(key: String): Long? = prefs.getLong(entryName(key), NEVER).takeIf { it != NEVER }

        override fun record(
            key: String,
            startedAt: Long,
            forgetBefore: Long,
        ) {
            val name = entryName(key)
            val editor = prefs.edit()
            prefs.all
                .filter { (existing, value) -> existing.startsWith(ENTRY_PREFIX) && (value as? Long ?: NEVER) < forgetBefore }
                .keys
                .forEach(editor::remove)
            editor.putLong(name, startedAt).apply()
        }

        /** The salt goes too, so nothing written later lines up with what was here. */
        override fun clear() = prefs.edit().clear().apply()

        private fun entryName(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest((salt() + key).toByteArray())
            return ENTRY_PREFIX + digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) }
        }

        @Synchronized
        private fun salt(): String =
            prefs.getString(SALT, null) ?: ByteArray(SALT_BYTES)
                .also(SecureRandom()::nextBytes)
                .joinToString("") { "%02x".format(it) }
                .also { prefs.edit().putString(SALT, it).apply() }

        private companion object {
            const val FILE = "session_rebuilds"
            const val SALT = "salt"
            const val ENTRY_PREFIX = "r:"
            const val NEVER = -1L
            const val SALT_BYTES = 16
            const val HASH_BYTES = 16
        }
    }
