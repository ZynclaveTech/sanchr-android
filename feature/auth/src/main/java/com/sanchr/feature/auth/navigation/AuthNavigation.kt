package com.sanchr.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.sanchr.feature.auth.LoginScreen
import com.sanchr.feature.auth.OtpScreen
import com.sanchr.feature.auth.ProfileScreen

const val AUTH_GRAPH_ROUTE = "auth"
const val LOGIN_ROUTE = "auth/login"
const val OTP_ROUTE = "auth/otp/{phoneNumber}"
const val REGISTER_ROUTE = "auth/register"

/**
 * Builds the authentication navigation graph with type-safe arguments.
 *
 * Routes:
 *   login -> otp/{phoneNumber} -> register
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthSuccess: () -> Unit,
) {
    navigation(startDestination = LOGIN_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(LOGIN_ROUTE) {
            // Task 3.6 will rewire this to an AuthState-driven host; for now the
            // screen consumes AuthViewModel directly and the host just renders it.
            LoginScreen()
        }

        composable(
            route = OTP_ROUTE,
            arguments =
                listOf(
                    navArgument("phoneNumber") {
                        type = NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val encodedPhone = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            val phoneNumber = java.net.URLDecoder.decode(encodedPhone, "UTF-8")
            OtpScreen(
                phoneNumber = phoneNumber,
                onVerified = { isNewUser ->
                    if (isNewUser) {
                        navController.navigate(REGISTER_ROUTE) {
                            // Don't allow going back to OTP after successful verification
                            popUpTo(OTP_ROUTE) { inclusive = true }
                        }
                    } else {
                        onAuthSuccess()
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(REGISTER_ROUTE) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
