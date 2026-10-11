package com.sanchr.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Removes reactions whose message is gone.
 *
 * `message_reactions` has no foreign key, on purpose: a reaction can arrive
 * before the message it is on, and has to wait for it. The cost was that
 * nothing ever removed one. Clear a chat, delete a message, let a
 * disappearing message expire — the message went and who reacted to it, with
 * what and when, stayed in the database for good.
 *
 * The deletes in `MessageDao` and `ConversationDao` now take the reactions
 * with them. This runs when the database opens and clears what was left
 * behind before they did, and anything a later way of deleting misses.
 */
internal object ReactionCleanup : RoomDatabase.Callback() {
    /**
     * How long a reaction may wait for a message that is not here yet. Far
     * longer than a message is ever held for delivery, so only a reaction
     * whose message is gone, not late, is taken.
     */
    const val ORPHAN_GRACE_MILLIS = 30L * 24 * 60 * 60 * 1000

    override fun onOpen(db: SupportSQLiteDatabase) = sweep(db, System.currentTimeMillis())

    fun sweep(
        db: SupportSQLiteDatabase,
        nowMillis: Long,
    ) {
        db.execSQL("DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE is_deleted = 1)")
        db.execSQL(
            """
            DELETE FROM message_reactions
            WHERE timestamp < ?
              AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.id = message_reactions.message_id)
            """.trimIndent(),
            arrayOf<Any>(nowMillis - ORPHAN_GRACE_MILLIS),
        )
    }
}
