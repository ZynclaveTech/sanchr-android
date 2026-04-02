package com.sanchr.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.auth.LoginScreen
import com.sanchr.feature.auth.OtpScreen
import com.sanchr.feature.auth.RegisterScreen

const val AUTH_GRAPH_ROUTE = "auth"
const val LOGIN_ROUTE = "auth/login"
const val OTP_ROUTE = "auth/otp/{phoneNumber}"
const val REGISTER_ROUTE = "auth/register"

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthSuccess: () -> Unit,
) {
    navigation(startDestination = LOGIN_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(LOGIN_ROUTE) {
            LoginScreen(
                onNavigateToOtp = { phoneNumber ->
                    navController.navigate("auth/otp/$phoneNumber")
                },
            )
        }

        composable(OTP_ROUTE) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            OtpScreen(
                phoneNumber = phoneNumber,
                onVerified = { isNewUser ->
                    if (isNewUser) {
                        navController.navigate(REGISTER_ROUTE)
                    } else {
                        onAuthSuccess()
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(REGISTER_ROUTE) {
            RegisterScreen(
                onRegistered = onAuthSuccess,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
