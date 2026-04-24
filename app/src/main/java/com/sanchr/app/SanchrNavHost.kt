package com.sanchr.app

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
import com.sanchr.feature.auth.navigation.authGraph
import com.sanchr.feature.calls.navigation.callsGraph
import com.sanchr.feature.chats.navigation.chatsGraph
import com.sanchr.feature.contacts.navigation.contactsGraph
import com.sanchr.feature.profile.navigation.profileGraph
import com.sanchr.feature.settings.navigation.settingsGraph
import com.sanchr.feature.vault.navigation.vaultGraph

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
    modifier: Modifier = Modifier,
    bootstrapViewModel: AppBootstrapViewModel = hiltViewModel(),
) {
    val startState by bootstrapViewModel.startDestination.collectAsState()

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
            ResolvedNavHost(
                startRoute = if (state is StartDestination.Main) "main" else "auth",
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ResolvedNavHost(
    startRoute: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )

            // Main authenticated flow with bottom navigation
            navigation(startDestination = TopLevelDestination.CHATS.route, route = "main") {
                chatsGraph(navController = navController)
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
