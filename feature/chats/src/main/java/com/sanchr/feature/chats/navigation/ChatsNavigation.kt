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
import com.sanchr.feature.chats.HiddenChatsScreen
import com.sanchr.feature.chats.info.ConversationInfoScreen
import com.sanchr.feature.chats.info.DisappearingMessagesScreen
import com.sanchr.feature.chats.info.WallpaperScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val CHATS_TAB_ROUTE = "chats_tab"
const val CHATS_LIST_ROUTE = "chats/list"
const val CHAT_DETAIL_ROUTE = "chats/detail/{conversationId}"
const val ARCHIVED_CHATS_ROUTE = "chats/archived"
const val HIDDEN_CHATS_ROUTE = "chats/hidden"
const val CONVERSATION_INFO_ROUTE = "chats/{conversationId}/info"
const val CONVERSATION_DISAPPEARING_ROUTE = "chats/{conversationId}/disappearing"
const val CONVERSATION_WALLPAPER_ROUTE = "chats/{conversationId}/wallpaper"

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
                onOpenHidden = { navController.navigate(HIDDEN_CHATS_ROUTE) },
            )
        }

        composable(
            route = CONVERSATION_INFO_ROUTE,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
            ConversationInfoScreen(
                onNavigateBack = { navController.popBackStack() },
                onVerifySafetyNumber = { peerId, peerName ->
                    val encoded = URLEncoder.encode(peerName, StandardCharsets.UTF_8.name())
                    navController.navigate("profile/$peerId/verify?name=$encoded")
                },
                onOpenDisappearing = { navController.navigate("chats/$conversationId/disappearing") },
                onOpenWallpaper = { navController.navigate("chats/$conversationId/wallpaper") },
                // The chat it describes is gone from this device, so the info
                // page and the transcript behind it both have to go.
                onLeft = { navController.popBackStack(CHATS_LIST_ROUTE, inclusive = false) },
            )
        }

        composable(
            route = CONVERSATION_DISAPPEARING_ROUTE,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) {
            DisappearingMessagesScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = CONVERSATION_WALLPAPER_ROUTE,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) {
            WallpaperScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(HIDDEN_CHATS_ROUTE) {
            HiddenChatsScreen(
                onConversationClick = { conversationId -> navController.navigate("chats/detail/$conversationId") },
                onNavigateBack = { navController.popBackStack() },
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
                onStartCall = onStartCall,
                onVerifySafetyNumber = { peerId, peerName ->
                    val encoded = URLEncoder.encode(peerName, StandardCharsets.UTF_8.name())
                    navController.navigate("profile/$peerId/verify?name=$encoded")
                },
                onOpenInfo = { navController.navigate("chats/$conversationId/info") },
            )
        }
    }
}
