package com.sanchr.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sanchr.feature.profile.ProfileScreen
import com.sanchr.feature.profile.verify.VerifySafetyNumberScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val PROFILE_ROUTE = "profile/{userId}"
const val VERIFY_SAFETY_NUMBER_ROUTE = "profile/{userId}/verify?name={name}"

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
            onVerifySafetyNumber = { name ->
                // The display name only labels the screen, so it travels in the
                // route rather than costing a second lookup. Encoded because a
                // name may hold spaces, slashes or emoji.
                val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                navController.navigate("profile/$userId/verify?name=$encoded")
            },
        )
    }

    composable(
        route = VERIFY_SAFETY_NUMBER_ROUTE,
        arguments =
            listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
    ) { backStackEntry ->
        VerifySafetyNumberScreen(
            contactName =
                backStackEntry.arguments
                    ?.getString("name")
                    .orEmpty()
                    .ifEmpty { "this contact" },
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
