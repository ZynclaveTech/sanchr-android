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
import com.sanchr.core.database.dao.AccountDao
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.EnvelopeQueueDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.dao.SignalIdentityDao
import com.sanchr.core.database.dao.SignalPreKeyDao
import com.sanchr.core.database.dao.SignalSessionDao
import com.sanchr.core.database.dao.SignalSignedPreKeyDao
import com.sanchr.core.database.entity.AccountEntity
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.EnvelopeQueueEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.database.entity.PendingMessageAckEntity
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
    ],
    version = 3,
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

    private val migration1To2 =
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

    private val migration2To3 =
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

    @Provides
    @Singleton
    fun provideSanchrDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): SanchrDatabase {
        ensureSqlCipherLoaded(context)
        return Room
            .databaseBuilder(
                context,
                SanchrDatabase::class.java,
                "sanchr-database",
            ).openHelperFactory(SupportOpenHelperFactory(passphraseProvider.obtainPassphrase()))
            .addMigrations(migration1To2, migration2To3)
            .build()
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
}
