package com.sanchr.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanchr.feature.profile.ProfileScreen

const val PROFILE_ROUTE = "profile/{userId}"

fun NavGraphBuilder.profileGraph(
    navController: NavController,
    /** Opens the call screen for this peer; the calls graph owns that route. */
    onStartCall: (peerId: String, peerName: String, isVideo: Boolean) -> Unit,
) {
    composable(PROFILE_ROUTE) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: ""
        ProfileScreen(
            userId = userId,
            onNavigateBack = { navController.popBackStack() },
            onOpenConversation = { conversationId -> navController.navigate("chats/detail/$conversationId") },
            onStartCall = onStartCall,
        )
    }
}
