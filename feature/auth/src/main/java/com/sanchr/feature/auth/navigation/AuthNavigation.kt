package com.sanchr.feature.auth.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.auth.AppLockGateScreen
import com.sanchr.feature.auth.AuthState
import com.sanchr.feature.auth.AuthViewModel
import com.sanchr.feature.auth.LoginScreen
import com.sanchr.feature.auth.OtpScreen
import com.sanchr.feature.auth.PermissionsScreen
import com.sanchr.feature.auth.ProfileScreen
import com.sanchr.feature.auth.RegistrationStep
import com.sanchr.feature.auth.SplashScreen

const val AUTH_GRAPH_ROUTE = "auth"
const val LOGIN_ROUTE = "auth/phone"
const val PROFILE_ROUTE = "auth/profile"
const val OTP_ROUTE = "auth/otp"
const val PERMISSIONS_ROUTE = "auth/permissions"

// --- Phase 1 realignment route constants (registered but unreachable) ------
// Real transitions land in Phase 5; these are declared now so Phase 2 screen
// stubs can reference stable route names and sub-agents working on navigation
// don't have to touch this file again.
const val SPLASH_ROUTE = "auth/splash"
const val HOME_ROUTE = "auth/home"
const val APP_LOCK_ROUTE = "auth/applock"
const val LOGIN_PHONE_ROUTE = "auth/login"
const val REGISTER_ROUTE = "auth/register"

/**
 * Authentication graph, driven by [AuthState]. All four screens share a single
 * [AuthViewModel] scoped to the `auth` nav graph (via `getBackStackEntry`), so
 * the flow's state survives route transitions.
 *
 *   PhoneEntry   -> LOGIN_ROUTE
 *   ProfileEntry -> PROFILE_ROUTE
 *   OtpEntry     -> OTP_ROUTE
 *   Permissions  -> PERMISSIONS_ROUTE
 *   Registering  -> stays on PERMISSIONS_ROUTE; overlay renders on top
 *   Done         -> fires [onAuthSuccess]
 *   Error        -> stays on current route; screens render inline error
 *
 * Each state screen is also responsible for rendering when its state is wrapped
 * in [AuthState.Error], so popping on error isn't required.
 *
 * ## Phase 1 realignment
 *
 * The `SPLASH_ROUTE`, `HOME_ROUTE`, `APP_LOCK_ROUTE`, `LOGIN_PHONE_ROUTE`, and
 * `REGISTER_ROUTE` destinations below are registered but **unreachable** —
 * `startDestination` is still [LOGIN_ROUTE] and nothing navigates to them.
 * Phase 5 will flip the start destination and wire `AuthFlowHost` to the new
 * [AuthState] subclasses. Screens attached to the new routes are placeholders
 * marked "TODO Phase 2" so a typo won't accidentally ship to users.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthSuccess: () -> Unit,
) {
    navigation(startDestination = LOGIN_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(LOGIN_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                LoginScreen(viewModel = vm)
            }
        }
        composable(PROFILE_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                ProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = vm,
                )
            }
        }
        composable(OTP_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                OtpScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = vm,
                )
            }
        }
        composable(PERMISSIONS_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                PermissionsScreen(viewModel = vm)
            }
        }

        // --- Phase 1 realignment scaffolds (registered, not reachable) ---
        composable(SPLASH_ROUTE) {
            SplashScreen(onSplashComplete = {})
        }
        composable(APP_LOCK_ROUTE) {
            AppLockGateScreen(onUnlocked = {})
        }
        composable(HOME_ROUTE) { PhasePlaceholder(label = "Home") }
        composable(LOGIN_PHONE_ROUTE) { PhasePlaceholder(label = "LoginPhone") }
        composable(REGISTER_ROUTE) { PhasePlaceholder(label = "Register") }
    }
}

/** Placeholder for Phase-2 screens. Never rendered in Phase 1 because nothing navigates here. */
@Composable
private fun PhasePlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "TODO Phase 2 — $label")
    }
}

/**
 * Shared wrapper each auth destination hosts its screen inside. Observes the
 * shared [AuthViewModel.state] and forwards route transitions, paints the
 * Registering overlay, and fires [onAuthSuccess] exactly once when state hits
 * [AuthState.Done].
 */
@Composable
private fun AuthFlowHost(
    vm: AuthViewModel,
    navController: NavController,
    onAuthSuccess: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (state) {
            is AuthState.PhoneEntry ->
                if (currentRoute != LOGIN_ROUTE) {
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(AUTH_GRAPH_ROUTE) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            is AuthState.ProfileEntry ->
                if (currentRoute != PROFILE_ROUTE) {
                    navController.navigate(PROFILE_ROUTE) { launchSingleTop = true }
                }
            is AuthState.OtpEntry ->
                if (currentRoute != OTP_ROUTE) {
                    navController.navigate(OTP_ROUTE) { launchSingleTop = true }
                }
            is AuthState.Permissions ->
                if (currentRoute != PERMISSIONS_ROUTE) {
                    // OTP is verified by this point; prevent back-nav into completed steps.
                    navController.navigate(PERMISSIONS_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            is AuthState.Registering -> Unit // overlay handles UX
            AuthState.Done -> onAuthSuccess()
            is AuthState.Error -> Unit // screens render inline
            // Phase 1 realignment states — unreachable today; Phase 5 will
            // extend this when statement with real routing. Listed explicitly
            // so the compiler's exhaustiveness check catches future additions.
            AuthState.Splash,
            AuthState.AppLocked,
            is AuthState.Home,
            is AuthState.LoginPhone,
            is AuthState.RegisterPhoneAndName,
            -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (state is AuthState.Registering) {
            RegisteringOverlay(step = (state as AuthState.Registering).step)
        }
    }
}

@Composable
private fun RegisteringOverlay(step: RegistrationStep) {
    val label =
        when (step) {
            RegistrationStep.GENERATING_KEYS -> "Generating keys\u2026"
            RegistrationStep.UPLOADING_KEYS -> "Uploading keys\u2026"
            RegistrationStep.FETCHING_SENDER_CERT -> "Fetching sender certificate\u2026"
            RegistrationStep.REGISTERING_PUSH -> "Registering for push\u2026"
            RegistrationStep.PERSISTING -> "Finishing setup\u2026"
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
    }
}
