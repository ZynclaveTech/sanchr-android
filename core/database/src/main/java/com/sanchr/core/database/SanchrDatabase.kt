package com.sanchr.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanchr.core.database.crypto.DatabasePassphraseProvider
import com.sanchr.core.database.dao.AccessKeyDao
import com.sanchr.core.database.dao.AccountDao
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ContactProfileDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.EnvelopeQueueDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.MessageReactionDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.dao.QuarantinedEnvelopeDao
import com.sanchr.core.database.dao.SignalIdentityDao
import com.sanchr.core.database.dao.SignalPreKeyDao
import com.sanchr.core.database.dao.SignalSessionDao
import com.sanchr.core.database.dao.SignalSignedPreKeyDao
import com.sanchr.core.database.entity.AccessKeyEntity
import com.sanchr.core.database.entity.AccountEntity
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ContactProfileEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.EnvelopeQueueEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.database.entity.MessageReactionEntity
import com.sanchr.core.database.entity.PendingMessageAckEntity
import com.sanchr.core.database.entity.QuarantinedEnvelopeEntity
import com.sanchr.core.database.entity.SignalIdentityEntity
import com.sanchr.core.database.entity.SignalPreKeyEntity
import com.sanchr.core.database.entity.SignalSessionEntity
import com.sanchr.core.database.entity.SignalSignedPreKeyEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        PendingMessageAckEntity::class,
        AccountEntity::class,
        SignalIdentityEntity::class,
        SignalSessionEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        EnvelopeQueueEntity::class,
        QuarantinedEnvelopeEntity::class,
        ContactProfileEntity::class,
        AccessKeyEntity::class,
        MessageReactionEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SanchrDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun conversationDao(): ConversationDao

    abstract fun contactDao(): ContactDao

    abstract fun pendingMessageAckDao(): PendingMessageAckDao

    abstract fun accountDao(): AccountDao

    abstract fun signalIdentityDao(): SignalIdentityDao

    abstract fun signalSessionDao(): SignalSessionDao

    abstract fun signalPreKeyDao(): SignalPreKeyDao

    abstract fun signalSignedPreKeyDao(): SignalSignedPreKeyDao

    abstract fun envelopeQueueDao(): EnvelopeQueueDao

    abstract fun quarantinedEnvelopeDao(): QuarantinedEnvelopeDao

    abstract fun contactProfileDao(): ContactProfileDao

    abstract fun accessKeyDao(): AccessKeyDao

    abstract fun messageReactionDao(): MessageReactionDao
}

/**
 * Type converters for Room. Handles serialization of complex types stored in columns.
 *
 * Currently entities use only primitive column types (String, Long, Int, Boolean).
 * Add @TypeConverter methods here when complex types (e.g. List, Instant, enums)
 * are introduced.
 */
class Converters {
    /**
     * Converts a comma-separated string to a list of strings.
     * Used when participant lists or tag lists are stored as a single column.
     */
    @TypeConverter
    fun fromStringList(value: String?): List<String> = value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    @TypeConverter
    fun toStringList(list: List<String>?): String = list?.joinToString(",") ?: ""
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Volatile
    private var sqlcipherLoaded = false

    private fun ensureSqlCipherLoaded(
        @Suppress("UNUSED_PARAMETER") context: Context,
    ) {
        if (sqlcipherLoaded) return
        synchronized(this) {
            if (sqlcipherLoaded) return
            // sqlcipher-android 4.6.1 does not expose SQLiteDatabase.loadLibs(Context).
            // The native library is loaded via the class's static initializer, but we
            // invoke System.loadLibrary defensively (idempotent) so any early DB use
            // does not race the static init.
            System.loadLibrary("sqlcipher")
            sqlcipherLoaded = true
        }
    }

    internal val migration1To2 =
        object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_message_acks` (
                        `conversation_id` TEXT NOT NULL,
                        `message_id` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`conversation_id`, `message_id`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_pending_message_acks_created_at`
                    ON `pending_message_acks` (`created_at`)
                    """.trimIndent(),
                )
            }
        }

    internal val migration2To3 =
        object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                        "`user_id` TEXT NOT NULL, " +
                        "`device_id` TEXT NOT NULL, " +
                        "`phone_e164` TEXT NOT NULL, " +
                        "`registration_id` INTEGER NOT NULL, " +
                        "`identity_private_key` BLOB, " +
                        "PRIMARY KEY(`user_id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signal_identities` (" +
                        "`address` TEXT NOT NULL, " +
                        "`identity_key` BLOB NOT NULL, " +
                        "`trust_level` INTEGER NOT NULL, " +
                        "`first_seen_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`address`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signal_sessions` (" +
                        "`address` TEXT NOT NULL, " +
                        "`session_record` BLOB NOT NULL, " +
                        "PRIMARY KEY(`address`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signal_prekeys` (" +
                        "`prekey_id` INTEGER NOT NULL, " +
                        "`record` BLOB NOT NULL, " +
                        "PRIMARY KEY(`prekey_id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `signal_signed_prekeys` (" +
                        "`prekey_id` INTEGER NOT NULL, " +
                        "`record` BLOB NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`prekey_id`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `envelope_queue` (" +
                        "`envelope_id` TEXT NOT NULL, " +
                        "`received_at` INTEGER NOT NULL, " +
                        "`processed` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "`payload` BLOB NOT NULL, " +
                        "PRIMARY KEY(`envelope_id`))",
                )
            }
        }

    internal val migration3To4 =
        object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quarantined_envelopes` (" +
                        "`envelope_id` TEXT NOT NULL, " +
                        "`received_at` INTEGER NOT NULL, " +
                        "`payload` BLOB NOT NULL, " +
                        "`sender_user_id` TEXT, " +
                        "`sender_device` INTEGER, " +
                        "`failure_class` TEXT NOT NULL, " +
                        "`failure_message` TEXT, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`envelope_id`))",
                )
            }
        }

    // Adds send-retry bookkeeping to `messages`. The existing `status` TEXT column
    // already carries the state machine (now extended with QUEUED in core:model);
    // we only need `attempts` + `last_attempt_at` for backoff scheduling.
    internal val migration4To5 =
        object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `messages` ADD COLUMN `attempts` INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE `messages` ADD COLUMN `last_attempt_at` INTEGER",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_messages_status_last_attempt_at` " +
                        "ON `messages` (`status`, `last_attempt_at`)",
                )
            }
        }

    // Adds per-row failure context to `messages`. `status = 'FAILED'` alone
    // doesn't let the UI distinguish "no recipients" from a peer-identity
    // rotation (which needs a safety-number screen, not a retry). M5 chat UI
    // keys off `failure_class` to render a distinct icon and off
    // `failure_reason` for the tooltip.
    internal val migration5To6 =
        object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `messages` ADD COLUMN `failure_reason` TEXT",
                )
                database.execSQL(
                    "ALTER TABLE `messages` ADD COLUMN `failure_class` TEXT",
                )
            }
        }

    // Profile Key: a peer's decrypted profile, keyed by user id, for anyone
    // who has messaged us — not only address-book contacts (whose
    // `phone_number` is unique, so an unknown sender cannot get a contact
    // row). See ContactProfileEntity.
    internal val migration6To7 =
        object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_profiles` (" +
                        "`user_id` TEXT NOT NULL, " +
                        "`display_name` TEXT, " +
                        "`bio` TEXT, " +
                        "`avatar_url` TEXT, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`user_id`))",
                )
            }
        }

    // Access keys: per-item AccessK for vault items (and later, media
    // attachments), kept only on the device that derived them. iOS keeps
    // the same table (`AccessKeyEntry`).
    internal val migration7To8 =
        object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `access_keys` (" +
                        "`media_id` TEXT NOT NULL, " +
                        "`access_key` BLOB NOT NULL, " +
                        "`conversation_id` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`last_accessed_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`media_id`))",
                )
            }
        }

    // Reactions: one row per (message, user, emoji), fed by `SendReaction`
    // echoes and `ReactionEvent`s on the stream. No FK to messages: an event
    // can precede the row it targets.
    internal val migration8To9 =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_reactions` (" +
                        "`message_id` TEXT NOT NULL, " +
                        "`user_id` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`message_id`, `user_id`, `emoji`))",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_message_reactions_message_id` ON `message_reactions` (`message_id`)")
            }
        }

    /**
     * Adds the manual safety-number verification timestamp. Nullable, so every
     * pre-existing identity starts out unverified rather than silently
     * inheriting a verification the user never performed.
     */
    internal val migration9To10 =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `signal_identities` ADD COLUMN `verified_at` INTEGER DEFAULT NULL")
            }
        }

    /**
     * Adds the unreviewed key change. Nullable, so every existing identity
     * starts with nothing pending rather than blocking sends on upgrade.
     */
    internal val migration10To11 =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `signal_identities` ADD COLUMN `pending_identity_key` BLOB DEFAULT NULL")
            }
        }

    /**
     * Adds the device-only "hidden from the chat lists" flag. Defaults to 0 so
     * an upgrade hides nothing the user did not hide themselves.
     */
    internal val migration11To12 =
        object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `is_hidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

    /**
     * Adds the per-conversation wallpaper. Nullable, so every existing chat
     * follows the account-wide choice until the user picks one for it.
     */
    internal val migration12To13 =
        object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `conversations` ADD COLUMN `wallpaper` TEXT DEFAULT NULL")
            }
        }

    @Provides
    @Singleton
    fun provideSanchrDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): SanchrDatabase {
        ensureSqlCipherLoaded(context)
        // SECURITY (M6 checklist #4): the passphrase plaintext is visible only
        // inside `withPassphrase { ... }`; the provider zero-fills its buffer
        // in a finally block on exit. We hand the factory a `copyOf()` because
        // sqlcipher-android 4.6.1's `SupportOpenHelperFactory` retains the
        // byte[] by reference and reuses it to reopen connections after WAL
        // checkpoints / forced close — if we zero the array the factory holds,
        // every subsequent reopen fails with "file is not a database". The
        // factory-owned copy is therefore the single unavoidable plaintext
        // residue in the process heap, documented as an upstream limitation.
        return passphraseProvider.withPassphrase { passphrase ->
            Room
                .databaseBuilder(
                    context,
                    SanchrDatabase::class.java,
                    "sanchr-database",
                ).openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
                .addMigrations(
                    migration1To2,
                    migration2To3,
                    migration3To4,
                    migration4To5,
                    migration5To6,
                    migration6To7,
                    migration7To8,
                    migration8To9,
                    migration9To10,
                    migration10To11,
                    migration11To12,
                    migration12To13,
                ).build()
        }
    }

    @Provides
    fun provideMessageDao(database: SanchrDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideConversationDao(database: SanchrDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideContactDao(database: SanchrDatabase): ContactDao = database.contactDao()

    @Provides
    fun providePendingMessageAckDao(database: SanchrDatabase): PendingMessageAckDao = database.pendingMessageAckDao()

    @Provides
    fun provideAccountDao(database: SanchrDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideSignalIdentityDao(database: SanchrDatabase): SignalIdentityDao = database.signalIdentityDao()

    @Provides
    fun provideSignalSessionDao(database: SanchrDatabase): SignalSessionDao = database.signalSessionDao()

    @Provides
    fun provideSignalPreKeyDao(database: SanchrDatabase): SignalPreKeyDao = database.signalPreKeyDao()

    @Provides
    fun provideSignalSignedPreKeyDao(database: SanchrDatabase): SignalSignedPreKeyDao = database.signalSignedPreKeyDao()

    @Provides
    fun provideEnvelopeQueueDao(database: SanchrDatabase): EnvelopeQueueDao = database.envelopeQueueDao()

    @Provides
    fun provideQuarantinedEnvelopeDao(database: SanchrDatabase): QuarantinedEnvelopeDao = database.quarantinedEnvelopeDao()

    @Provides
    fun provideContactProfileDao(database: SanchrDatabase): ContactProfileDao = database.contactProfileDao()

    @Provides
    fun provideAccessKeyDao(database: SanchrDatabase): AccessKeyDao = database.accessKeyDao()

    @Provides
    fun provideMessageReactionDao(database: SanchrDatabase): MessageReactionDao = database.messageReactionDao()
}
