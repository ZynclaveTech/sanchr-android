package com.sanchr.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanchr.feature.profile.ProfileScreen

const val PROFILE_ROUTE = "profile/{userId}"

fun NavGraphBuilder.profileGraph(navController: NavController) {
    composable(PROFILE_ROUTE) { backStackEntry ->
        val userId = backStackEntry.arguments?.getString("userId") ?: ""
        ProfileScreen(
            userId = userId,
            onNavigateBack = { navController.popBackStack() },
            onStartChat = { conversationId ->
                navController.navigate("chats/detail/$conversationId")
            },
            onStartCall = { targetUserId ->
                // TODO: Navigate to active call screen
            },
        )
    }
}
