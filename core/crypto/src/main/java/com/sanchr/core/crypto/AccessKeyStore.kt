package com.sanchr.core.crypto

import com.sanchr.core.database.dao.AccessKeyDao
import com.sanchr.core.database.entity.AccessKeyEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-item access keys with a sliding 30-day expiry, as iOS
 * `AccessKeyStore`: an entry is live while either its creation or its
 * last successful use is within the window. Reading through [getAndTouch]
 * extends the window; [purgeExpired] reaps what fell out of it.
 */
@Singleton
class AccessKeyStore internal constructor(
    private val dao: AccessKeyDao,
    private val nowMillis: () -> Long,
) {
    @Inject
    constructor(dao: AccessKeyDao) : this(dao, System::currentTimeMillis)

    enum class Kind(
        /** iOS `AccessKeyEntry.Kind` raw value; also what vault metadata carries as `kind`. */
        val wire: String,
    ) {
        MESSAGE_MEDIA("messageMedia"),
        VAULT_AUTO_VAULTED("vaultAutoVaulted"),
        VAULT_MANUAL("vaultManual"),
    }

    suspend fun store(
        mediaId: String,
        accessKey: ByteArray,
        kind: Kind,
        conversationId: String = "",
    ) {
        val now = nowMillis()
        dao.upsert(AccessKeyEntity(mediaId, accessKey, conversationId, kind.wire, createdAt = now, lastAccessedAt = now))
    }

    /** The key if present and unexpired, without extending its window. */
    suspend fun retrieve(mediaId: String): ByteArray? = live(mediaId)?.accessKey

    /** The key if present and unexpired, extending its window — the entry point for decrypt paths. */
    suspend fun getAndTouch(mediaId: String): ByteArray? {
        val entry = live(mediaId) ?: return null
        dao.touch(mediaId, nowMillis())
        return entry.accessKey
    }

    suspend fun delete(mediaId: String) = dao.delete(mediaId)

    /** @return how many expired entries were removed. */
    suspend fun purgeExpired(): Int = dao.purgeExpired(nowMillis() - TTL_MILLIS)

    suspend fun deleteAll() = dao.deleteAll()

    private suspend fun live(mediaId: String): AccessKeyEntity? {
        val entry = dao.get(mediaId) ?: return null
        val anchor = maxOf(entry.createdAt, entry.lastAccessedAt)
        return if (nowMillis() - anchor > TTL_MILLIS) null else entry
    }

    companion object {
        const val TTL_MILLIS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
