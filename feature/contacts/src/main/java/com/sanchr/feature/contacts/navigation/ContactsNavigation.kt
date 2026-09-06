package com.sanchr.feature.contacts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.sanchr.feature.contacts.ContactSyncScreen
import com.sanchr.feature.contacts.ContactsScreen
import com.sanchr.feature.contacts.lookup.PhoneLookupScreen

const val CONTACTS_TAB_ROUTE = "contacts_tab"
const val CONTACTS_LIST_ROUTE = "contacts/list"
const val CONTACT_SYNC_ROUTE = "contacts/sync"
const val PHONE_LOOKUP_ROUTE = "contacts/lookup"

fun NavGraphBuilder.contactsGraph(navController: NavController) {
    navigation(startDestination = CONTACTS_LIST_ROUTE, route = CONTACTS_TAB_ROUTE) {
        composable(CONTACTS_LIST_ROUTE) {
            ContactsScreen(
                onContactClick = { userId ->
                    navController.navigate("chats/detail/$userId")
                },
                onSyncContacts = {
                    navController.navigate(CONTACT_SYNC_ROUTE)
                },
                onFindByNumber = { navController.navigate(PHONE_LOOKUP_ROUTE) },
            )
        }

        composable(PHONE_LOOKUP_ROUTE) {
            PhoneLookupScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenConversation = { conversationId ->
                    navController.navigate("chats/detail/$conversationId")
                },
            )
        }

        composable(CONTACT_SYNC_ROUTE) {
            ContactSyncScreen(
                onSyncComplete = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
