package com.sanchr.feature.chats.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.sanchr.feature.chats.ArchivedChatsScreen
import com.sanchr.feature.chats.ChatDetailScreen
import com.sanchr.feature.chats.ChatsListScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val CHATS_TAB_ROUTE = "chats_tab"
const val CHATS_LIST_ROUTE = "chats/list"
const val CHAT_DETAIL_ROUTE = "chats/detail/{conversationId}"
const val ARCHIVED_CHATS_ROUTE = "chats/archived"

/**
 * Builds the chats navigation graph.
 *
 * Routes:
 *   chats/list -> chats/detail/{conversationId}
 */
fun NavGraphBuilder.chatsGraph(
    navController: NavController,
    /** Opens the call screen for [peerId]; the calls graph owns that route. */
    onStartCall: (peerId: String, peerName: String, isVideo: Boolean) -> Unit,
) {
    navigation(startDestination = CHATS_LIST_ROUTE, route = CHATS_TAB_ROUTE) {
        composable(CHATS_LIST_ROUTE) {
            ChatsListScreen(
                onConversationClick = { conversationId ->
                    navController.navigate("chats/detail/$conversationId")
                },
                onOpenConversation = { conversationId ->
                    navController.navigate("chats/detail/$conversationId")
                },
                onOpenArchived = { navController.navigate(ARCHIVED_CHATS_ROUTE) },
            )
        }

        composable(ARCHIVED_CHATS_ROUTE) {
            ArchivedChatsScreen(
                onConversationClick = { conversationId -> navController.navigate("chats/detail/$conversationId") },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = CHAT_DETAIL_ROUTE,
            arguments =
                listOf(
                    navArgument("conversationId") {
                        type = NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatDetailScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { userId ->
                    navController.navigate("profile/$userId")
                },
                onStartCall = onStartCall,
                onVerifySafetyNumber = { peerId, peerName ->
                    val encoded = URLEncoder.encode(peerName, StandardCharsets.UTF_8.name())
                    navController.navigate("profile/$peerId/verify?name=$encoded")
                },
            )
        }
    }
}
