package com.sanchr.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuarantineMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SanchrDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate_3_to_4_creates_quarantined_envelopes_table() {
        helper.createDatabase("migration-test", 3).close()
        helper.runMigrationsAndValidate("migration-test", 4, true).use { db ->
            db
                .query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='quarantined_envelopes'",
                ).use { c ->
                    check(c.moveToFirst()) { "missing table quarantined_envelopes" }
                }

            val expectedColumns =
                setOf(
                    "envelope_id",
                    "received_at",
                    "payload",
                    "sender_user_id",
                    "sender_device",
                    "failure_class",
                    "failure_message",
                    "attempts",
                )
            val actual = mutableSetOf<String>()
            db.query("PRAGMA table_info(`quarantined_envelopes`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    actual.add(c.getString(nameIdx))
                }
            }
            check(actual == expectedColumns) { "column mismatch: expected=$expectedColumns actual=$actual" }
        }
    }
}
