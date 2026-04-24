package com.sanchr.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.onboarding.OnboardingAvatarScreen
import com.sanchr.feature.onboarding.OnboardingContactSyncScreen
import com.sanchr.feature.onboarding.OnboardingNameScreen
import com.sanchr.feature.onboarding.OnboardingState
import com.sanchr.feature.onboarding.OnboardingViewModel
import com.sanchr.feature.onboarding.OnboardingWelcomeScreen

const val ONBOARDING_GRAPH_ROUTE = "onboarding"
const val ONBOARDING_WELCOME = "onboarding/welcome"
const val ONBOARDING_NAME = "onboarding/name"
const val ONBOARDING_AVATAR = "onboarding/avatar"
const val ONBOARDING_CONTACT_SYNC = "onboarding/contactsync"

/**
 * Onboarding navigation graph.
 *
 * Structural reference: `feature/auth/.../navigation/AuthNavigation.kt:75-136`
 * (`authGraph`). Like `authGraph`, this graph scopes a single
 * [OnboardingViewModel] to the graph's back-stack entry via
 * `getBackStackEntry(ONBOARDING_GRAPH_ROUTE)` and observes [OnboardingState]
 * inside a shared `OnboardingFlowHost` wrapper that converts state to route.
 *
 * Terminal state [OnboardingState.Completed] fires [onOnboardingComplete]
 * instead of navigating — Phase 5 wires that callback in `:app`'s NavHost to
 * flip the `has_completed_onboarding` DataStore key and navigate to Main.
 *
 * This file defines the graph-builder function only; it is NOT yet attached
 * to `:app`'s NavHost. Phase 5 integrates it.
 */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onOnboardingComplete: () -> Unit,
) {
    navigation(startDestination = ONBOARDING_WELCOME, route = ONBOARDING_GRAPH_ROUTE) {
        composable(ONBOARDING_WELCOME) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) }
            val vm: OnboardingViewModel = hiltViewModel(parent)
            OnboardingFlowHost(vm = vm, navController = navController, onComplete = onOnboardingComplete) {
                OnboardingWelcomeScreen(viewModel = vm)
            }
        }
        composable(ONBOARDING_NAME) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) }
            val vm: OnboardingViewModel = hiltViewModel(parent)
            OnboardingFlowHost(vm = vm, navController = navController, onComplete = onOnboardingComplete) {
                OnboardingNameScreen(viewModel = vm)
            }
        }
        composable(ONBOARDING_AVATAR) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) }
            val vm: OnboardingViewModel = hiltViewModel(parent)
            OnboardingFlowHost(vm = vm, navController = navController, onComplete = onOnboardingComplete) {
                OnboardingAvatarScreen(viewModel = vm)
            }
        }
        composable(ONBOARDING_CONTACT_SYNC) { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(ONBOARDING_GRAPH_ROUTE) }
            val vm: OnboardingViewModel = hiltViewModel(parent)
            OnboardingFlowHost(vm = vm, navController = navController, onComplete = onOnboardingComplete) {
                OnboardingContactSyncScreen(viewModel = vm)
            }
        }
    }
}

/**
 * Maps an [OnboardingState] to a destination route, or null when the state is
 * either terminal (Completed — caller fires onComplete) or renders inline
 * (Error — current screen shows a banner).
 */
private data class OnboardingRouteTarget(
    val route: String?,
    val popUpTo: String? = null,
    val popInclusive: Boolean = false,
    val terminal: Boolean = false,
)

private fun OnboardingState.toRouteTarget(): OnboardingRouteTarget =
    when (this) {
        OnboardingState.Welcome -> OnboardingRouteTarget(route = ONBOARDING_WELCOME)
        is OnboardingState.NameEntry -> OnboardingRouteTarget(route = ONBOARDING_NAME)
        is OnboardingState.AvatarEntry -> OnboardingRouteTarget(route = ONBOARDING_AVATAR)
        is OnboardingState.ContactSync ->
            // Prevent back-nav into completed steps once permissions are being granted.
            OnboardingRouteTarget(
                route = ONBOARDING_CONTACT_SYNC,
                popUpTo = ONBOARDING_GRAPH_ROUTE,
                popInclusive = false,
            )
        OnboardingState.Completed -> OnboardingRouteTarget(route = null, terminal = true)
        is OnboardingState.Error -> OnboardingRouteTarget(route = null)
    }

@Composable
private fun OnboardingFlowHost(
    vm: OnboardingViewModel,
    navController: NavController,
    onComplete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val target = state.toRouteTarget()
        if (target.terminal) {
            onComplete()
            return@LaunchedEffect
        }
        val desired = target.route ?: return@LaunchedEffect
        val current = navController.currentBackStackEntry?.destination?.route
        if (current == desired) return@LaunchedEffect
        navController.navigate(desired) { applyPopOptions(target) }
    }

    content()
}

private fun NavOptionsBuilder.applyPopOptions(target: OnboardingRouteTarget) {
    launchSingleTop = true
    val popTarget = target.popUpTo ?: return
    popUpTo(popTarget) { inclusive = target.popInclusive }
}
