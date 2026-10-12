package com.sanchr.app.data

import android.util.Log
import com.sanchr.core.database.dao.ConversationDao
import com.sanchr.core.database.dao.MessageDao
import com.sanchr.core.datastore.SessionManager
import com.sanchr.core.datastore.UserPreferences
import com.sanchr.core.notifications.NotificationHandler
import com.sanchr.domain.messaging.ContactProfileResolver
import com.sanchr.domain.messaging.DirectChatMembership
import com.sanchr.domain.messaging.MessageMediaEvictor
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/**
 * Checks the stand-in conversations made for messages that arrived before
 * their chat did, once the server has said whose chats they are.
 *
 * A stand-in is filed under an id its first message chose, with members that
 * are a guess, named after whoever wrote first. If that id turns out to be
 * this user's one-to-one chat with someone else, none of that may stay: the
 * stranger's messages are removed — as they would have been refused had the
 * chat been known when they arrived — and so are their name, their picture
 * and their messages' share of the unread count.
 *
 * Only conversations that began as stand-ins are ever checked this way.
 */
class PlaceholderConversations
    @Inject
    constructor(
        private val userPreferences: UserPreferences,
        private val conversationDao: ConversationDao,
        private val messageDao: MessageDao,
        private val sessionManager: SessionManager,
        private val contactProfileResolver: ContactProfileResolver,
        private val notificationHandler: NotificationHandler,
        // Lazy: the evictor reads messages through the repository this serves.
        private val mediaEvictor: Lazy<MessageMediaEvictor>,
    ) {
        /**
         * @param confirmedIds conversations the server has just listed.
         * @return how many messages were removed.
         */
        suspend fun settle(confirmedIds: Set<String>): Int {
            val settled = userPreferences.placeholderConversations.first().intersect(confirmedIds)
            if (settled.isEmpty()) return 0
            val removed = settled.sumOf { removeOutsiders(it) }
            userPreferences.removePlaceholderConversations(settled)
            return removed
        }

        /** Removes what was filed in [conversationId] by someone who is not in it; how many went. */
        private suspend fun removeOutsiders(conversationId: String): Int {
            val conversation = conversationDao.getConversationById(conversationId) ?: return 0
            val members = participantIds(conversation.participantIds)
            val self = sessionManager.getUserId()
            val outsiders =
                messageDao
                    .sendersInConversation(conversationId)
                    .filterNot { DirectChatMembership.allows(conversation.type, members, it.senderId, self) }
                    .map { it.id }
            if (outsiders.isEmpty()) return 0
            val evictor = mediaEvictor.get()
            val files = outsiders.flatMap { evictor.evict(it) }
            outsiders.chunked(ID_QUERY_CHUNK).forEach { messageDao.deleteMessages(it) }
            evictor.again(files)
            notificationHandler.cancelNotificationsForConversation(conversationId)
            conversationDao.updateConversation(
                conversation.copy(
                    title = members.firstOrNull { it != self }?.let { contactProfileResolver.displayNameFor(it) },
                    avatarUrl = null,
                    unreadCount = (conversation.unreadCount - outsiders.size).coerceAtLeast(0),
                ),
            )
            Log.w(TAG, "removed ${outsiders.size} message(s) filed in a one-to-one chat their sender is not in")
            return outsiders.size
        }

        private fun participantIds(raw: String): List<String> =
            runCatching {
                val array = JSONArray(raw.trim())
                List(array.length()) { array.optString(it) }
            }.getOrElse { emptyList() }

        private companion object {
            const val TAG = "PlaceholderConvs"

            /** Ids per `IN (...)` query, well under SQLite's limit on bound variables. */
            const val ID_QUERY_CHUNK = 500
        }
    }
