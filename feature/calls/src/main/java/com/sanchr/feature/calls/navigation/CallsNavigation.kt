package com.sanchr.feature.calls.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.sanchr.feature.calls.ActiveCallScreen
import com.sanchr.feature.calls.CallsListScreen
import com.sanchr.feature.calls.OutgoingCallRequest

const val CALLS_TAB_ROUTE = "calls_tab"
const val CALLS_LIST_ROUTE = "calls/list"
const val ACTIVE_CALL_ROUTE = "calls/active/{callId}?action={action}&peer={peer}&name={name}"

/** Placeholder call id for a call that has not been placed yet; the manager assigns the real one. */
private const val NEW_CALL_ID = "new"
private const val ACTION_VOICE = "voice"
private const val ACTION_VIDEO = "video"

/** The route that opens the call screen and places a call to [peerId] once permissions are granted. */
fun outgoingCallRoute(
    peerId: String,
    peerName: String,
    isVideo: Boolean,
): String =
    "calls/active/$NEW_CALL_ID?action=${if (isVideo) ACTION_VIDEO else ACTION_VOICE}" +
        "&peer=${Uri.encode(peerId)}&name=${Uri.encode(peerName)}"

/** The outgoing request encoded in a route's arguments, or null for an existing (e.g. incoming) call. */
internal fun outgoingRequestFrom(
    action: String?,
    peer: String?,
    name: String?,
): OutgoingCallRequest? {
    if (peer.isNullOrBlank()) return null
    val isVideo =
        when (action) {
            ACTION_VOICE -> false
            ACTION_VIDEO -> true
            else -> return null
        }
    return OutgoingCallRequest(peerId = peer, peerName = name?.takeIf { it.isNotBlank() } ?: peer, isVideo = isVideo)
}

fun NavGraphBuilder.callsGraph(navController: NavController) {
    navigation(startDestination = CALLS_LIST_ROUTE, route = CALLS_TAB_ROUTE) {
        composable(CALLS_LIST_ROUTE) {
            CallsListScreen(
                onStartCall = { peerId, peerName, isVideo ->
                    navController.navigate(outgoingCallRoute(peerId, peerName, isVideo))
                },
            )
        }

        composable(
            route = ACTIVE_CALL_ROUTE,
            arguments =
                listOf(
                    navArgument("action") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("peer") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val callId = args?.getString("callId") ?: ""
            ActiveCallScreen(
                callId = callId,
                outgoing = outgoingRequestFrom(args?.getString("action"), args?.getString("peer"), args?.getString("name")),
                onCallEnded = { navController.popBackStack() },
            )
        }
    }
}
