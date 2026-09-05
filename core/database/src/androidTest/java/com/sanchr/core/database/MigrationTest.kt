package com.sanchr.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SanchrDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate_2_to_3_creates_signal_tables() {
        helper.createDatabase("migration-test", 2).close()
        helper.runMigrationsAndValidate("migration-test", 3, true, DatabaseModule.migration2To3).use { db ->
            val tables =
                listOf(
                    "accounts",
                    "signal_identities",
                    "signal_sessions",
                    "signal_prekeys",
                    "signal_signed_prekeys",
                    "envelope_queue",
                )
            tables.forEach { name ->
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").use { c ->
                    check(c.moveToFirst()) { "missing table $name" }
                }
            }
        }
    }

    @Test
    fun migrate_6_to_7_creates_contact_profiles() {
        helper.createDatabase("migration-test-7", 6).close()
        // validateMigration = true diffs the migrated schema against the
        // exported 7.json, so a hand-written CREATE TABLE that drifts from
        // the entity fails here rather than on a user's device. The
        // migration under test is the production one from DatabaseModule.
        helper.runMigrationsAndValidate("migration-test-7", 7, true, DatabaseModule.migration6To7).use { db ->
            db.query("PRAGMA table_info(`contact_profiles`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns == listOf("user_id", "display_name", "bio", "avatar_url", "updated_at")) {
                    "unexpected contact_profiles columns: $columns"
                }
            }
        }
    }

    @Test
    fun migrate_7_to_8_creates_access_keys() {
        helper.createDatabase("migration-test-8", 7).close()
        helper.runMigrationsAndValidate("migration-test-8", 8, true, DatabaseModule.migration7To8).use { db ->
            db.query("PRAGMA table_info(`access_keys`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns == listOf("media_id", "access_key", "conversation_id", "kind", "created_at", "last_accessed_at")) {
                    "unexpected access_keys columns: $columns"
                }
            }
        }
    }
}
