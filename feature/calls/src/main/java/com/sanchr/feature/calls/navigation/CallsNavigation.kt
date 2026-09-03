package com.sanchr.feature.calls.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.sanchr.feature.calls.ActiveCallScreen
import com.sanchr.feature.calls.CallsListScreen

const val CALLS_TAB_ROUTE = "calls_tab"
const val CALLS_LIST_ROUTE = "calls/list"
const val ACTIVE_CALL_ROUTE = "calls/active/{callId}?action={action}"

fun NavGraphBuilder.callsGraph(navController: NavController) {
    navigation(startDestination = CALLS_LIST_ROUTE, route = CALLS_TAB_ROUTE) {
        composable(CALLS_LIST_ROUTE) {
            CallsListScreen(
                onCallClick = { userId ->
                    // TODO: Initiate call and navigate to active call screen
                },
            )
        }

        composable(
            route = ACTIVE_CALL_ROUTE,
            arguments =
                listOf(
                    navArgument("action") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            ActiveCallScreen(
                callId = callId,
                onCallEnded = { navController.popBackStack() },
            )
        }
    }
}
