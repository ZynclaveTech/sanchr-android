package com.sanchr.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.dao.PendingMessageAckDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
import com.sanchr.core.database.entity.PendingMessageAckEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        PendingMessageAckEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SanchrDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun conversationDao(): ConversationDao

    abstract fun contactDao(): ContactDao

    abstract fun pendingMessageAckDao(): PendingMessageAckDao
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

    @Provides
    @Singleton
    fun provideSanchrDatabase(
        @ApplicationContext context: Context,
    ): SanchrDatabase =
        Room
            .databaseBuilder(
                context,
                SanchrDatabase::class.java,
                "sanchr-database",
            )
            // TODO: Add SQLCipher for encrypted database storage
            // .openHelperFactory(SupportFactory(passphrase))
            .addMigrations(migration1To2)
            .build()

    @Provides
    fun provideMessageDao(database: SanchrDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideConversationDao(database: SanchrDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideContactDao(database: SanchrDatabase): ContactDao = database.contactDao()

    @Provides
    fun providePendingMessageAckDao(database: SanchrDatabase): PendingMessageAckDao = database.pendingMessageAckDao()
}
