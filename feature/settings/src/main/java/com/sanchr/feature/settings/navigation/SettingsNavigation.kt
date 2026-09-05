package com.sanchr.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.settings.AppearanceScreen
import com.sanchr.feature.settings.BlockedContactsScreen
import com.sanchr.feature.settings.ChatSettingsScreen
import com.sanchr.feature.settings.EncryptionKeysScreen
import com.sanchr.feature.settings.HelpCenterScreen
import com.sanchr.feature.settings.NotificationsScreen
import com.sanchr.feature.settings.PrivacyScreen
import com.sanchr.feature.settings.RegistrationLockScreen
import com.sanchr.feature.settings.SecurityScreen
import com.sanchr.feature.settings.SettingsScreen
import com.sanchr.feature.settings.StorageScreen

const val SETTINGS_TAB_ROUTE = "settings_tab"
const val SETTINGS_MAIN_ROUTE = "settings/main"
const val SETTINGS_APPEARANCE_ROUTE = "settings/appearance"
const val SETTINGS_NOTIFICATIONS_ROUTE = "settings/notifications"
const val SETTINGS_PRIVACY_ROUTE = "settings/privacy"
const val SETTINGS_SECURITY_ROUTE = "settings/security"
const val SETTINGS_STORAGE_ROUTE = "settings/storage"
const val SETTINGS_CHAT_ROUTE = "settings/chat"
const val SETTINGS_ENCRYPTION_KEYS_ROUTE = "settings/encryption-keys"
const val SETTINGS_HELP_ROUTE = "settings/help"
const val SETTINGS_BLOCKED_ROUTE = "settings/blocked"
const val SETTINGS_REGISTRATION_LOCK_ROUTE = "settings/registration-lock"

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation(startDestination = SETTINGS_MAIN_ROUTE, route = SETTINGS_TAB_ROUTE) {
        composable(SETTINGS_MAIN_ROUTE) {
            SettingsScreen(
                onNavigateToAppearance = { navController.navigate(SETTINGS_APPEARANCE_ROUTE) },
                onNavigateToNotifications = { navController.navigate(SETTINGS_NOTIFICATIONS_ROUTE) },
                onNavigateToPrivacy = { navController.navigate(SETTINGS_PRIVACY_ROUTE) },
                onNavigateToSecurity = { navController.navigate(SETTINGS_SECURITY_ROUTE) },
                onNavigateToStorage = { navController.navigate(SETTINGS_STORAGE_ROUTE) },
                onNavigateToChatSettings = { navController.navigate(SETTINGS_CHAT_ROUTE) },
                onNavigateToEncryptionKeys = { navController.navigate(SETTINGS_ENCRYPTION_KEYS_ROUTE) },
                onNavigateToHelp = { navController.navigate(SETTINGS_HELP_ROUTE) },
                onNavigateToProfile = { navController.navigate("profile/me") },
            )
        }

        composable(SETTINGS_APPEARANCE_ROUTE) {
            AppearanceScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_NOTIFICATIONS_ROUTE) {
            NotificationsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_PRIVACY_ROUTE) {
            PrivacyScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBlockedContacts = { navController.navigate(SETTINGS_BLOCKED_ROUTE) },
            )
        }

        composable(SETTINGS_BLOCKED_ROUTE) {
            BlockedContactsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_SECURITY_ROUTE) {
            SecurityScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegistrationLock = { navController.navigate(SETTINGS_REGISTRATION_LOCK_ROUTE) },
            )
        }

        composable(SETTINGS_REGISTRATION_LOCK_ROUTE) {
            RegistrationLockScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_STORAGE_ROUTE) {
            StorageScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_CHAT_ROUTE) {
            ChatSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_ENCRYPTION_KEYS_ROUTE) {
            EncryptionKeysScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(SETTINGS_HELP_ROUTE) {
            HelpCenterScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
