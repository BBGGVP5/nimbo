package com.danila.nimbo.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

fun isInternetAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val hasUsableTransport =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    // VALIDATED может запаздывать/быть недоступным на части мобильных сетей.
    // Для UX считаем сеть доступной, если есть INTERNET capability и реальный transport.
    return hasInternetCapability && hasUsableTransport
}

fun observeInternetConnection(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (manager == null) {
        trySend(false)
        close()
        return@callbackFlow
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(isInternetAvailable(context))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(isInternetAvailable(context))
        }

        override fun onLost(network: Network) {
            trySend(isInternetAvailable(context))
        }
    }

    trySend(isInternetAvailable(context))
    manager.registerDefaultNetworkCallback(callback)
    awaitClose {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }
}.conflate()
