package com.sanchr.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sanchr.core.database.dao.ContactDao
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.database.entity.ContactEntity
import com.sanchr.core.database.entity.ConversationEntity
import com.sanchr.core.database.entity.MessageEntity
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SanchrDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
}

/**
 * Type converters for Room. Handles serialization of complex types stored in columns.
 */
class Converters {
    // TODO: Add @TypeConverter methods for:
    //   - List<String> <-> String (JSON array)
    //   - Instant <-> Long (epoch millis)
    //   - MessageStatus <-> String (enum name)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSanchrDatabase(
        @ApplicationContext context: Context,
    ): SanchrDatabase {
        return Room.databaseBuilder(
            context,
            SanchrDatabase::class.java,
            "sanchr-database",
        )
            // TODO: Add SQLCipher for encrypted database storage
            // .openHelperFactory(SupportFactory(passphrase))
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMessageDao(database: SanchrDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideConversationDao(database: SanchrDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideContactDao(database: SanchrDatabase): ContactDao = database.contactDao()
}
