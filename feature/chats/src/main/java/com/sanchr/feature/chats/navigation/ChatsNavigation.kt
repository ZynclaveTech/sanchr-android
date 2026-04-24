package com.sanchr.feature.chats.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.sanchr.feature.chats.ChatDetailScreen
import com.sanchr.feature.chats.ChatsListScreen

const val CHATS_TAB_ROUTE = "chats_tab"
const val CHATS_LIST_ROUTE = "chats/list"
const val CHAT_DETAIL_ROUTE = "chats/detail/{conversationId}"

/**
 * Builds the chats navigation graph.
 *
 * Routes:
 *   chats/list -> chats/detail/{conversationId}
 */
fun NavGraphBuilder.chatsGraph(navController: NavController) {
    navigation(startDestination = CHATS_LIST_ROUTE, route = CHATS_TAB_ROUTE) {
        composable(CHATS_LIST_ROUTE) {
            ChatsListScreen(
                onConversationClick = { conversationId ->
                    navController.navigate("chats/detail/$conversationId")
                },
                onOpenConversation = { conversationId ->
                    navController.navigate("chats/detail/$conversationId")
                },
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
            )
        }
    }
}
