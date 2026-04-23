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
        helper.runMigrationsAndValidate("migration-test", 3, true).use { db ->
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
}
