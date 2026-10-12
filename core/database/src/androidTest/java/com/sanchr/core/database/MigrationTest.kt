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

    @Test
    fun migrate_8_to_9_creates_message_reactions() {
        helper.createDatabase("migration-test-9", 8).close()
        helper.runMigrationsAndValidate("migration-test-9", 9, true, DatabaseModule.migration8To9).use { db ->
            db.query("PRAGMA table_info(`message_reactions`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns == listOf("message_id", "user_id", "emoji", "timestamp")) { "unexpected message_reactions columns: $columns" }
            }
        }
    }

    @Test
    fun migrate_9_to_10_adds_identity_verified_at() {
        helper.createDatabase("migration-test-10", 9).close()
        helper.runMigrationsAndValidate("migration-test-10", 10, true, DatabaseModule.migration9To10).use { db ->
            db.query("PRAGMA table_info(`signal_identities`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns == listOf("address", "identity_key", "trust_level", "first_seen_at", "verified_at")) {
                    "unexpected signal_identities columns: $columns"
                }
            }
        }
    }

    @Test
    fun migrate_10_to_11_adds_pending_identity_key() {
        helper.createDatabase("migration-test-11", 10).close()
        helper.runMigrationsAndValidate("migration-test-11", 11, true, DatabaseModule.migration10To11).use { db ->
            db.query("PRAGMA table_info(`signal_identities`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(
                    columns ==
                        listOf("address", "identity_key", "trust_level", "first_seen_at", "verified_at", "pending_identity_key"),
                ) {
                    "unexpected signal_identities columns: $columns"
                }
            }
        }
    }

    @Test
    fun migrate_11_to_12_adds_conversation_is_hidden_defaulting_to_visible() {
        helper.createDatabase("migration-test-12", 11).close()
        helper.runMigrationsAndValidate("migration-test-12", 12, true, DatabaseModule.migration11To12).use { db ->
            db.query("PRAGMA table_info(`conversations`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns.contains("is_hidden")) { "conversations is missing is_hidden: $columns" }
            }
            // An upgrade must not hide anything the user did not hide.
            db.query("SELECT COUNT(*) FROM conversations WHERE is_hidden != 0").use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "migration hid existing conversations" }
            }
        }
    }

    @Test
    fun migrate_12_to_13_adds_conversation_wallpaper_defaulting_to_the_account_choice() {
        helper.createDatabase("migration-test-13", 12).close()
        helper.runMigrationsAndValidate("migration-test-13", 13, true, DatabaseModule.migration12To13).use { db ->
            db.query("PRAGMA table_info(`conversations`)").use { c ->
                val columns = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                check(columns.contains("wallpaper")) { "conversations is missing wallpaper: $columns" }
            }
            // Null means "follow the account-wide choice", so an upgrade must
            // not pin every chat to a colour nobody picked.
            db.query("SELECT COUNT(*) FROM conversations WHERE wallpaper IS NOT NULL").use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "migration set a wallpaper on existing conversations" }
            }
        }
    }

    @Test
    fun migrate_14_to_15_adds_the_vault_policy_with_everything_off() {
        helper.createDatabase("migration-test-15", 14).use { db ->
            db.execSQL(
                "INSERT INTO conversations (id, type, participant_ids, unread_count, is_pinned, is_muted, " +
                    "is_archived, is_hidden, updated_at, created_at) VALUES ('c1', 'DIRECT', '[]', 0, 0, 0, 0, 0, 1, 1)",
            )
        }
        helper.runMigrationsAndValidate("migration-test-15", 15, true, DatabaseModule.migration14To15).use { db ->
            // An upgrade must not start vaulting, hiding or expiring media in
            // a chat whose owner never asked for it.
            db
                .query(
                    "SELECT vault_auto_incoming, vault_view_once, vault_capture_protection FROM conversations WHERE id = 'c1'",
                ).use { c ->
                    check(c.moveToFirst()) { "the existing conversation did not survive the migration" }
                    check(c.getInt(0) == 0 && c.getInt(1) == 0 && c.getInt(2) == 0) { "migration turned a policy flag on" }
                }
        }
    }

    @Test
    fun migrate_15_to_16_carries_existing_blocks_into_their_own_table() {
        helper.createDatabase("migration-test-16", 15).use { db ->
            db.execSQL(
                "INSERT INTO contacts (id, user_id, phone_number, display_name, is_registered, is_blocked, is_favorite, " +
                    "last_synced_at) VALUES ('alice', 'alice', '+1', 'Alice', 1, 0, 0, 7), ('bob', 'bob', '+2', 'Bob', 1, 1, 0, 9)",
            )
        }
        helper.runMigrationsAndValidate("migration-test-16", 16, true, DatabaseModule.migration15To16).use { db ->
            db.query("SELECT user_id FROM blocked_users").use { c ->
                check(c.count == 1 && c.moveToFirst() && c.getString(0) == "bob") { "exactly the blocked contact must carry over" }
            }
        }
    }

    @Test
    fun migrate_16_to_17_adds_an_empty_record_of_deleted_messages() {
        helper.createDatabase("migration-test-17", 16).close()
        helper.runMigrationsAndValidate("migration-test-17", 17, true, DatabaseModule.migration16To17).use { db ->
            db.query("SELECT COUNT(*) FROM deleted_messages").use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "nothing is known to have been deleted before this" }
            }
        }
    }

    @Test
    fun migrate_17_to_18_adds_an_empty_place_to_hold_unfiled_messages() {
        helper.createDatabase("migration-test-18", 17).close()
        helper.runMigrationsAndValidate("migration-test-18", 18, true, DatabaseModule.migration17To18).use { db ->
            db.query("SELECT COUNT(*) FROM held_incoming").use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "nothing is held before anything has failed" }
            }
        }
    }
}
