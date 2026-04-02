package com.sanchr.feature.vault.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sanchr.feature.vault.VaultScreen

const val VAULT_ROUTE = "vault"

fun NavGraphBuilder.vaultGraph(navController: NavController) {
    composable(VAULT_ROUTE) {
        VaultScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
