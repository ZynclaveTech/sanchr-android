package com.sanchr.core.crypto

import com.sanchr.core.database.dao.AccessKeyDao
import com.sanchr.core.database.entity.AccessKeyEntity
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class AccessKeyStoreTest {
    private class FakeDao : AccessKeyDao {
        val rows = mutableMapOf<String, AccessKeyEntity>()

        override suspend fun get(mediaId: String) = rows[mediaId]

        override suspend fun upsert(entry: AccessKeyEntity) {
            rows[entry.mediaId] = entry
        }

        override suspend fun touch(
            mediaId: String,
            nowMillis: Long,
        ) {
            rows[mediaId]?.let { rows[mediaId] = it.copy(lastAccessedAt = nowMillis) }
        }

        override suspend fun purgeExpired(cutoffMillis: Long): Int {
            val expired = rows.filterValues { maxOf(it.createdAt, it.lastAccessedAt) < cutoffMillis }.keys
            expired.forEach { rows.remove(it) }
            return expired.size
        }

        override suspend fun delete(mediaId: String) {
            rows.remove(mediaId)
        }

        override suspend fun deleteAll() = rows.clear()
    }

    private val dao = FakeDao()
    private var now = 1_000_000L
    private val store = AccessKeyStore(dao) { now }
    private val key = ByteArray(32) { 3 }
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `stores with the iOS kind raw value and reads back`() =
        runTest {
            store.store("v-1", key, AccessKeyStore.Kind.VAULT_MANUAL)
            assertEquals("vaultManual", dao.rows.getValue("v-1").kind)
            assertContentEquals(key, store.retrieve("v-1"))
        }

    @Test
    fun `an entry expires 30 days after its last use, and a touch extends it`() =
        runTest {
            store.store("v-1", key, AccessKeyStore.Kind.VAULT_MANUAL)
            now += 29 * day
            assertContentEquals(key, store.getAndTouch("v-1")) // touched at day 29
            now += 29 * day // day 58: within 30 days of the touch
            assertContentEquals(key, store.retrieve("v-1"))
            now += 2 * day // day 60: 31 days since the touch
            assertNull(store.retrieve("v-1"))
            assertNull(store.getAndTouch("v-1"))
        }

    @Test
    fun `purgeExpired removes only what fell out of the window`() =
        runTest {
            store.store("old", key, AccessKeyStore.Kind.VAULT_MANUAL)
            now += 31 * day
            store.store("new", key, AccessKeyStore.Kind.VAULT_MANUAL)
            assertEquals(1, store.purgeExpired())
            assertEquals(setOf("new"), dao.rows.keys)
        }
}
