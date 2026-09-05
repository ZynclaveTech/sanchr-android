package com.sanchr.app

import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.sanchr.app.bootstrap.AppBootstrapViewModel
import com.sanchr.app.bootstrap.StartDestination
import com.sanchr.app.navigation.PendingDestination
import com.sanchr.feature.auth.navigation.authGraph
import com.sanchr.feature.calls.navigation.callsGraph
import com.sanchr.feature.calls.navigation.outgoingCallRoute
import com.sanchr.feature.chats.navigation.chatsGraph
import com.sanchr.feature.contacts.navigation.contactsGraph
import com.sanchr.feature.onboarding.navigation.ONBOARDING_GRAPH_ROUTE
import com.sanchr.feature.onboarding.navigation.onboardingGraph
import com.sanchr.feature.profile.navigation.profileGraph
import com.sanchr.feature.settings.navigation.settingsGraph
import com.sanchr.feature.vault.navigation.vaultGraph
import kotlinx.coroutines.flow.StateFlow

/**
 * Top-level navigation destinations for the bottom bar.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    CHATS(
        route = "chats_tab",
        label = "Chats",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat,
    ),
    CALLS(
        route = "calls_tab",
        label = "Calls",
        selectedIcon = Icons.Filled.Call,
        unselectedIcon = Icons.Outlined.Call,
    ),
    CONTACTS(
        route = "contacts_tab",
        label = "Contacts",
        selectedIcon = Icons.Filled.Contacts,
        unselectedIcon = Icons.Outlined.Contacts,
    ),
    SETTINGS(
        route = "settings_tab",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

@Composable
fun SanchrNavHost(
    pendingDestination: StateFlow<PendingDestination?>,
    onPendingConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    bootstrapViewModel: AppBootstrapViewModel = hiltViewModel(),
) {
    val startState by bootstrapViewModel.startDestination.collectAsState()
    val hasCompletedOnboarding by bootstrapViewModel.hasCompletedOnboarding.collectAsState()

    when (val state = startState) {
        is StartDestination.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is StartDestination.Auth, is StartDestination.Main -> {
            // Start route resolution matrix (see has_completed_onboarding plan):
            //   - no session                              → "auth"
            //   - session & onboarding completed           → "main"
            //   - session & onboarding NOT completed       → "onboarding"
            // Returning users on a fresh install will land here with
            // StartDestination.Main but flag=false and therefore see
            // onboarding once; that is intentional per the per-device
            // gating design (plan Phase 5).
            val startRoute =
                when (state) {
                    StartDestination.Main ->
                        if (hasCompletedOnboarding) "main" else ONBOARDING_GRAPH_ROUTE
                    StartDestination.Auth -> "auth"
                    StartDestination.Loading -> "auth" // unreachable, compiler exhaustiveness
                }
            ResolvedNavHost(
                startRoute = startRoute,
                bootstrapViewModel = bootstrapViewModel,
                pendingDestination = pendingDestination,
                onPendingConsumed = onPendingConsumed,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ResolvedNavHost(
    startRoute: String,
    bootstrapViewModel: AppBootstrapViewModel,
    pendingDestination: StateFlow<PendingDestination?>,
    onPendingConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val sessionActive by bootstrapViewModel.sessionActive.collectAsState()
    val hasCompletedOnboarding by bootstrapViewModel.hasCompletedOnboarding.collectAsState()

    // Reactive logout: if the session flips off while we're inside the main
    // graph, pop back to the auth graph. This covers logout from any screen
    // (Settings, a future force-logout on 401, etc.) without a process restart.
    //
    // currentDestination is included as a second key so the effect re-fires
    // if it was transiently null when sessionActive first flipped false — the
    // retry happens on the next recomposition once the back-stack entry settles.
    LaunchedEffect(sessionActive, currentDestination) {
        if (!sessionActive) {
            val inAuthenticatedArea =
                currentDestination?.hierarchy?.any {
                    it.route == "main" || it.route == ONBOARDING_GRAPH_ROUTE
                } == true
            if (inAuthenticatedArea) {
                navController.navigate("auth") {
                    popUpTo("main") { inclusive = true }
                    popUpTo(ONBOARDING_GRAPH_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Deliver a notification-tap destination once the session is confirmed
    // active AND onboarding is complete. A tap that arrives while signed out,
    // before startup has resolved, or while the user is still mid-onboarding
    // must not push a chat/call screen ahead of profile setup and contact
    // sync -- so the pending value is held (not dropped) and this effect
    // re-fires on every sessionActive/hasCompletedOnboarding change until it
    // can be delivered.
    val pending by pendingDestination.collectAsState()
    LaunchedEffect(pending, sessionActive, hasCompletedOnboarding) {
        val destination = pending ?: return@LaunchedEffect
        if (!sessionActive || !hasCompletedOnboarding) return@LaunchedEffect
        navController.navigate(destination.route) { launchSingleTop = true }
        onPendingConsumed()
    }

    val topLevelDestinations = remember { TopLevelDestination.entries }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Only show bottom bar when on a top-level destination
            val showBottomBar =
                topLevelDestinations.any { dest ->
                    currentDestination?.hierarchy?.any { it.route == dest.route } == true
                }
            if (showBottomBar) {
                SanchrBottomBar(
                    destinations = topLevelDestinations,
                    currentDestination = currentDestination,
                    onNavigateToDestination = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300),
                )
            },
        ) {
            // Auth flow (unauthenticated)
            authGraph(
                navController = navController,
                onAuthSuccess = {
                    // Post-OTP routing gate. Read the flag's latest value at
                    // click-time (captured via the enclosing composable's
                    // `hasCompletedOnboarding` state). New-user path: flag is
                    // false (default), navigate to onboarding graph.
                    // Returning-user path on the same device: flag was set
                    // true by a prior onboarding, skip straight to main.
                    // Returning-user path on a new device: flag is false, see
                    // onboarding once (design decision — per-device semantics;
                    // plan Phase 5).
                    val target = if (hasCompletedOnboarding) "main" else ONBOARDING_GRAPH_ROUTE
                    Log.d(
                        "AuthFlow",
                        "SanchrNavHost.onAuthSuccess: hasCompletedOnboarding=$hasCompletedOnboarding -> $target",
                    )
                    navController.navigate(target) {
                        popUpTo("auth") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )

            // Onboarding flow (authenticated, first run on this device)
            onboardingGraph(
                navController = navController,
                onOnboardingComplete = {
                    navController.navigate("main") {
                        popUpTo(ONBOARDING_GRAPH_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )

            // Main authenticated flow with bottom navigation
            navigation(startDestination = TopLevelDestination.CHATS.route, route = "main") {
                chatsGraph(
                    navController = navController,
                    onStartCall = { peerId, peerName, isVideo -> navController.navigate(outgoingCallRoute(peerId, peerName, isVideo)) },
                )
                callsGraph(navController = navController)
                contactsGraph(navController = navController)
                settingsGraph(navController = navController)
            }

            // Standalone flows (no bottom bar)
            profileGraph(navController = navController)
            vaultGraph(navController = navController)
        }
    }
}

@Composable
private fun SanchrBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        destinations.forEach { destination ->
            val selected =
                currentDestination?.hierarchy?.any {
                    it.route == destination.route
                } == true

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(text = destination.label) },
            )
        }
    }
}
