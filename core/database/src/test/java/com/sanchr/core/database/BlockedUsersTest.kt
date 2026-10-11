package com.sanchr.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.sanchr.core.database.entity.BlockedUserEntity
import com.sanchr.core.database.entity.ContactEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A block is its own record, so it can be on anyone — contact or not. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class BlockedUsersTest {
    private lateinit var db: SanchrDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SanchrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun contact(
        id: String,
        phone: String,
    ) = ContactEntity(id = id, userId = id, phoneNumber = phone, displayName = id, isRegistered = true, isFavorite = true)

    private suspend fun block(userId: String) = db.blockedUserDao().block(BlockedUserEntity(userId, blockedAt = 5))

    @Test
    fun `someone with no contact row can be blocked and unblocked`() =
        runTest {
            assertFalse(db.blockedUserDao().isBlocked("stranger"))

            block("stranger")
            assertTrue(db.blockedUserDao().isBlocked("stranger"))

            db.blockedUserDao().unblock("stranger")
            assertFalse(db.blockedUserDao().isBlocked("stranger"))
        }

    @Test
    fun `blocking again keeps one record`() =
        runTest {
            block("u1")
            block("u1")

            assertEquals(listOf("u1"), db.blockedUserDao().blockedUserIds())
        }

    @Test
    fun `a blocked contact leaves every contact list and returns when unblocked`() =
        runTest {
            db.contactDao().insertContacts(listOf(contact("alice", "+1"), contact("bob", "+2")))
            block("bob")

            assertEquals(
                listOf("alice"),
                db
                    .contactDao()
                    .observeContacts()
                    .first()
                    .map { it.id },
            )
            assertEquals(
                listOf("alice"),
                db
                    .contactDao()
                    .observeRegisteredContacts()
                    .first()
                    .map { it.id },
            )
            assertEquals(
                listOf("alice"),
                db
                    .contactDao()
                    .observeFavoriteContacts()
                    .first()
                    .map { it.id },
            )

            db.blockedUserDao().unblock("bob")

            assertEquals(
                listOf("alice", "bob"),
                db
                    .contactDao()
                    .observeContacts()
                    .first()
                    .map { it.id },
            )
        }

    /** A contact sync rewrites contact rows; a block is not on one any more. */
    @Test
    fun `a contact sync does not unblock anyone`() =
        runTest {
            db.contactDao().insertContacts(listOf(contact("bob", "+2")))
            block("bob")

            db.contactDao().insertContacts(listOf(contact("bob", "+2")))

            assertTrue(db.blockedUserDao().isBlocked("bob"))
        }

    // ── Upgrade ──────────────────────────────────────────────────────────

    private fun version15(): SupportSQLiteOpenHelper {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(15) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // `contacts` as schema 15 declares it.
                            db.execSQL(
                                "CREATE TABLE `contacts` (`id` TEXT NOT NULL, `user_id` TEXT, `phone_number` TEXT NOT NULL, " +
                                    "`display_name` TEXT NOT NULL, `avatar_url` TEXT, `is_registered` INTEGER NOT NULL, " +
                                    "`is_blocked` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `last_synced_at` INTEGER, " +
                                    "PRIMARY KEY(`id`))",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun SupportSQLiteDatabase.oldContact(
        id: String,
        userId: String?,
        blocked: Boolean,
        syncedAt: Long?,
    ) = execSQL(
        "INSERT INTO contacts VALUES (?, ?, ?, 'name', NULL, 1, ?, 0, ?)",
        arrayOf(id, userId, "+$id", if (blocked) 1 else 0, syncedAt),
    )

    @Test
    fun `upgrading carries over everyone already blocked and nobody else`() {
        version15().use { helper ->
            val old = helper.writableDatabase
            old.oldContact("alice", "alice", blocked = false, syncedAt = 7)
            old.oldContact("bob", "bob", blocked = true, syncedAt = 9)
            old.oldContact("row-3", "carol", blocked = true, syncedAt = null)

            DatabaseModule.migration15To16.migrate(old)

            val carried = mutableMapOf<String, Long>()
            old.query("SELECT user_id, blocked_at FROM blocked_users").use { rows ->
                while (rows.moveToNext()) carried[rows.getString(0)] = rows.getLong(1)
            }
            assertEquals(mapOf("bob" to 9L, "carol" to 0L), carried)
        }
    }

    @Test
    fun `upgrading twice is harmless`() {
        version15().use { helper ->
            val old = helper.writableDatabase
            old.oldContact("bob", "bob", blocked = true, syncedAt = 9)

            DatabaseModule.migration15To16.migrate(old)
            DatabaseModule.migration15To16.migrate(old)

            old.query("SELECT COUNT(*) FROM blocked_users").use { rows ->
                rows.moveToFirst()
                assertEquals(1, rows.getInt(0))
            }
        }
    }
}
