package com.sanchr.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes network connectivity state changes via [ConnectivityManager].
 * Emits true when the device has internet connectivity, false otherwise.
 */
@Singleton
class ConnectivityMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        /**
         * Returns the current network connectivity status synchronously.
         */
        val isConnected: Boolean
            get() {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

        /**
         * A [Flow] that emits connectivity status changes. Uses [callbackFlow] to bridge
         * the callback-based [ConnectivityManager] API into a reactive stream.
         */
        val connectivityFlow: Flow<Boolean> =
            callbackFlow {
                val networkRequest =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val hasInternet =
                                networkCapabilities
                                    .hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            val isValidated =
                                networkCapabilities
                                    .hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            trySend(hasInternet && isValidated)
                        }
                    }

                connectivityManager.registerNetworkCallback(networkRequest, callback)

                // Emit initial state
                trySend(isConnected)

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.conflate().distinctUntilChanged()
    }
