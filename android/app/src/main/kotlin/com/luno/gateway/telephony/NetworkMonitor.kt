package com.luno.gateway.telephony

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.luno.gateway.logging.LunoLogger
import com.luno.gateway.model.NetworkStatus

class NetworkMonitor(
    private val context: Context,
    private val store: DeviceStateStore,
    private val logger: LunoLogger,
) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private var callback: ConnectivityManager.NetworkCallback? = null

    // Shared by the UI telemetry stream and the backend connection (M13); ref-counted
    // so one releasing it doesn't unregister the callback the other still needs.
    private var refCount = 0

    fun start() {
        if (refCount++ > 0) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(capabilitiesOf(network))
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = publish(capabilities)

            override fun onLost(network: Network) = publish(null)
            override fun onUnavailable() = publish(null)
        }
        callback = cb
        connectivityManager.registerDefaultNetworkCallback(cb)
        // Seed the current state: the callback does not fire on registration when
        // already offline.
        publish(capabilitiesOf(connectivityManager.activeNetwork))
        logger.i(TAG, "network monitoring started")
    }

    fun stop() {
        if (refCount == 0) return
        if (--refCount > 0) return
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        callback = null
    }

    private fun capabilitiesOf(network: Network?): NetworkCapabilities? =
        network?.let { connectivityManager.getNetworkCapabilities(it) }

    private fun publish(capabilities: NetworkCapabilities?) {
        store.updateNetwork(statusFrom(capabilities))
    }

    private fun statusFrom(capabilities: NetworkCapabilities?): NetworkStatus {
        if (capabilities == null) {
            return NetworkStatus(connected = false, validated = false, transport = "NONE", metered = false)
        }
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
        return NetworkStatus(
            connected = true,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            transport = transport,
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
