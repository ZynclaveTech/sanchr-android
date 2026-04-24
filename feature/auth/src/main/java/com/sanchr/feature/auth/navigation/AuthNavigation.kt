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
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.auth.AppLockGateScreen
import com.sanchr.feature.auth.AuthState
import com.sanchr.feature.auth.AuthViewModel
import com.sanchr.feature.auth.HomeScreen
import com.sanchr.feature.auth.LoginPhoneScreen
import com.sanchr.feature.auth.OtpScreen
import com.sanchr.feature.auth.PermissionsScreen
import com.sanchr.feature.auth.ProfileScreen
import com.sanchr.feature.auth.RegisterScreen
import com.sanchr.feature.auth.RegistrationStep
import com.sanchr.feature.auth.SplashScreen

const val AUTH_GRAPH_ROUTE = "auth"

// Legacy routes — unreachable as of Phase 2 (startDestination flipped to SPLASH_ROUTE)
// but retained so the existing state machine + unit tests keep compiling. Phase 4
// removes them alongside the corresponding `PhoneEntry` / `ProfileEntry` /
// `Permissions` states.
const val LOGIN_ROUTE = "auth/phone"
const val PROFILE_ROUTE = "auth/profile"
const val OTP_ROUTE = "auth/otp"
const val PERMISSIONS_ROUTE = "auth/permissions"

// New iOS-parity routes. `startDestination` is `SPLASH_ROUTE`; from there the
// `AuthFlowHost` observer drives route transitions based on `AuthState`.
const val SPLASH_ROUTE = "auth/splash"
const val HOME_ROUTE = "auth/home"
const val APP_LOCK_ROUTE = "auth/applock"
const val LOGIN_PHONE_ROUTE = "auth/login"
const val REGISTER_ROUTE = "auth/register"

/**
 * Authentication graph, driven by [AuthState]. All screens share a single
 * [AuthViewModel] scoped to the `auth` nav graph (via `getBackStackEntry`), so
 * the flow's state survives route transitions.
 *
 * Route mapping (Phase 2):
 *
 *   Splash               -> SPLASH_ROUTE              (startDestination)
 *   Home                 -> HOME_ROUTE
 *   AppLocked            -> APP_LOCK_ROUTE
 *   LoginPhone           -> LOGIN_PHONE_ROUTE
 *   RegisterPhoneAndName -> REGISTER_ROUTE
 *   OtpEntry             -> OTP_ROUTE
 *   Permissions          -> PERMISSIONS_ROUTE
 *   Registering          -> stays on current route; overlay renders on top
 *   Done                 -> fires [onAuthSuccess]
 *   Error                -> stays on current route; screens render inline error
 *
 * The legacy `PhoneEntry` / `ProfileEntry` states still map to `LOGIN_ROUTE` /
 * `PROFILE_ROUTE` for safety during the cross-phase transition; nothing seeds
 * those states under the Phase 2 flow so the branches are unreachable.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthSuccess: () -> Unit,
) {
    navigation(startDestination = SPLASH_ROUTE, route = AUTH_GRAPH_ROUTE) {
        composable(SPLASH_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                SplashScreen(onSplashComplete = vm::onSplashComplete)
            }
        }
        composable(APP_LOCK_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                // Phase-2: AppLockGate is a stub; unlocking hands control back to the
                // NavHost which routes to Home (the default authenticated-but-no-session
                // landing under the Phase-2 state machine).
                AppLockGateScreen(onUnlocked = onAuthSuccess)
            }
        }
        composable(HOME_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                HomeScreen(viewModel = vm)
            }
        }
        composable(LOGIN_PHONE_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                LoginPhoneScreen(viewModel = vm)
            }
        }
        composable(REGISTER_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                RegisterScreen(viewModel = vm)
            }
        }
        composable(LOGIN_ROUTE) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AUTH_GRAPH_ROUTE) }
            val vm: AuthViewModel = hiltViewModel(parent)
            AuthFlowHost(vm = vm, navController = navController, onAuthSuccess = onAuthSuccess) {
                // Legacy phone-entry screen — unreachable under Phase 2 but retained
                // so the legacy state machine + unit tests keep compiling. Phase 4
                // removes this composable.
                LoginPhoneScreen(viewModel = vm)
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
    }
}

/**
 * Describes how a given [AuthState] maps to a destination route + popUpTo
 * policy. Extracted from [AuthFlowHost] so the composable stays under detekt's
 * cyclomatic-complexity budget as the state machine grows.
 *
 * @property route route to navigate to, or `null` when the state renders as an
 *   overlay / inline error / terminal callback on the current destination.
 * @property popUpTo optional `popUpTo` target for the navigation options block.
 * @property popInclusive whether `popUpTo` is inclusive of the target route.
 * @property terminal when true, the state is a terminal signal (Done) and
 *   [AuthFlowHost] should fire `onAuthSuccess` instead of navigating.
 */
private data class RouteTarget(
    val route: String?,
    val popUpTo: String? = null,
    val popInclusive: Boolean = false,
    val terminal: Boolean = false,
)

private fun AuthState.toRouteTarget(): RouteTarget =
    when (this) {
        AuthState.Splash -> RouteTarget(route = SPLASH_ROUTE)
        AuthState.AppLocked -> RouteTarget(route = APP_LOCK_ROUTE)
        is AuthState.Home ->
            // Splash is one-shot; drop it from the back stack so Back from Home
            // exits the app (see realignment plan §8 RISK#1).
            RouteTarget(route = HOME_ROUTE, popUpTo = SPLASH_ROUTE, popInclusive = true)
        is AuthState.LoginPhone -> RouteTarget(route = LOGIN_PHONE_ROUTE)
        is AuthState.RegisterPhoneAndName -> RouteTarget(route = REGISTER_ROUTE)
        is AuthState.PhoneEntry ->
            RouteTarget(route = LOGIN_ROUTE, popUpTo = AUTH_GRAPH_ROUTE, popInclusive = false)
        is AuthState.ProfileEntry -> RouteTarget(route = PROFILE_ROUTE)
        is AuthState.OtpEntry -> RouteTarget(route = OTP_ROUTE)
        is AuthState.Permissions ->
            // OTP is verified by this point; prevent back-nav into completed steps.
            RouteTarget(route = PERMISSIONS_ROUTE, popUpTo = AUTH_GRAPH_ROUTE, popInclusive = false)
        AuthState.Done -> RouteTarget(route = null, terminal = true)
        is AuthState.Registering, is AuthState.Error -> RouteTarget(route = null)
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
        val target = state.toRouteTarget()
        if (target.terminal) {
            onAuthSuccess()
            return@LaunchedEffect
        }
        val desiredRoute = target.route ?: return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute == desiredRoute) return@LaunchedEffect
        navController.navigate(desiredRoute) { applyPopOptions(target) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (state is AuthState.Registering) {
            RegisteringOverlay(step = (state as AuthState.Registering).step)
        }
    }
}

private fun NavOptionsBuilder.applyPopOptions(target: RouteTarget) {
    launchSingleTop = true
    val popTarget = target.popUpTo ?: return
    popUpTo(popTarget) { inclusive = target.popInclusive }
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
